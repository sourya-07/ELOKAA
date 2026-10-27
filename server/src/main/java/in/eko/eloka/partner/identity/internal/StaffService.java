package in.eko.eloka.partner.identity.internal;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import in.eko.eloka.partner.audit.AuditEntry;
import in.eko.eloka.partner.audit.AuditLog;
import in.eko.eloka.partner.identity.StaffDirectory;
import in.eko.eloka.partner.identity.StaffProperties;
import in.eko.eloka.partner.identity.StaffView;
import in.eko.eloka.partner.integration.GoogleIdentityVerifier;
import in.eko.eloka.partner.shared.config.SafetyCheck;
import in.eko.eloka.partner.shared.crypto.BlindIndex;
import in.eko.eloka.partner.shared.crypto.FieldCipher;
import in.eko.eloka.partner.shared.crypto.Masking;
import in.eko.eloka.partner.shared.crypto.Mobiles;
import in.eko.eloka.partner.shared.error.NotFoundException;
import in.eko.eloka.partner.shared.error.ValidationException;
import in.eko.eloka.partner.shared.security.Role;
import in.eko.eloka.partner.shared.security.SessionKind;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

interface StaffUserRepository extends JpaRepository<StaffUser, UUID> {

	Optional<StaffUser> findByEmailHash(byte[] emailHash);

	Optional<StaffUser> findByMobileHash(byte[] mobileHash);
}

/**
 * Staff accounts and sign-in: Google in every environment (only accounts of the configured
 * Workspace domain that have a staff account), OTP to a registered mobile only when
 * {@code eloka.staff.otp-login-enabled} (local/test; staging and production refuse to start with it).
 */
@Service
class StaffService implements StaffDirectory, SafetyCheck {

	/** Result of a staff sign-in. Unknown and disabled accounts get the same answer. */
	sealed interface SignIn {

		record SignedIn(TokenPair tokens, StaffView staff) implements SignIn {
		}

		record NotAllowed() implements SignIn {
		}

		record WrongCode(int attemptsLeft) implements SignIn {
		}

		record Expired() implements SignIn {
		}
	}

	private final StaffUserRepository staff;
	private final OtpService otps;
	private final SessionService sessions;
	private final GoogleIdentityVerifier google;
	private final StaffProperties properties;
	private final BlindIndex index;
	private final FieldCipher cipher;
	private final AuditLog audit;
	private final TransactionTemplate transactions;

	StaffService(StaffUserRepository staff, OtpService otps, SessionService sessions, GoogleIdentityVerifier google,
			StaffProperties properties, BlindIndex index, FieldCipher cipher, AuditLog audit,
			TransactionTemplate transactions) {
		this.staff = staff;
		this.otps = otps;
		this.sessions = sessions;
		this.google = google;
		this.properties = properties;
		this.index = index;
		this.cipher = cipher;
		this.audit = audit;
		this.transactions = transactions;
	}

	/** Local/test only. Unknown numbers get a silent challenge: same limits, same answers, no SMS. */
	void sendOtp(String mobile, String clientIp) {
		requireOtpSignIn();
		String normalized = Mobiles.normalize(mobile);
		boolean known = staff.findByMobileHash(index.hash("staff_mobile", normalized)).filter(StaffUser::isActive).isPresent();
		otps.issue(OtpChallenge.PURPOSE_STAFF_SIGN_IN, normalized, clientIp, known);
	}

	SignIn verifyOtp(String mobile, String code) {
		requireOtpSignIn();
		String normalized = Mobiles.normalize(mobile);
		OtpService.Check check = otps.check(OtpChallenge.PURPOSE_STAFF_SIGN_IN, normalized, code);
		if (check instanceof OtpService.Check.NoLiveOtp) {
			return new SignIn.Expired();
		}
		if (check instanceof OtpService.Check.Wrong wrong) {
			return new SignIn.WrongCode(wrong.attemptsLeft());
		}
		return transactions.execute(tx -> staff.findByMobileHash(index.hash("staff_mobile", normalized))
			.filter(StaffUser::isActive)
			.<SignIn>map(user -> open(user, "OTP"))
			.orElseGet(SignIn.NotAllowed::new));
	}

	@Transactional
	SignIn googleSignIn(String idToken) {
		Optional<GoogleIdentityVerifier.GoogleIdentity> identity = google.verify(idToken);
		String domain = properties.googleDomain();
		// No domain configured = nobody: a personal Google account must never pass as staff
		if (identity.isEmpty() || !identity.get().emailVerified() || identity.get().email() == null || domain == null
				|| domain.isBlank() || !domain.equalsIgnoreCase(identity.get().hostedDomain())) {
			return refused(null, "TOKEN_OR_DOMAIN");
		}
		Optional<StaffUser> user = staff.findByEmailHash(emailHash(identity.get().email().toLowerCase(Locale.ROOT)));
		if (user.isEmpty()) {
			return refused(null, "UNKNOWN_ACCOUNT");
		}
		if (!user.get().isActive()) {
			return refused(user.get().getId().toString(), "ACCOUNT_DISABLED");
		}
		return open(user.get(), "GOOGLE");
	}

	/** Creates or updates a staff account (bootstrap command and the local seed). */
	@Transactional
	StaffView upsert(String email, String displayName, String mobile, Set<Role> roles, boolean active) {
		String normalizedEmail = email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
		if (!normalizedEmail.matches("[^@\\s]+@[^@\\s]+\\.[^@\\s]+")) {
			throw ValidationException.field("email", "Enter a valid email address");
		}
		Optional<StaffUser> existing = staff.findByEmailHash(emailHash(normalizedEmail));
		if (existing.isEmpty() && roles.isEmpty()) {
			throw ValidationException.field("roles", "Give the staff member at least one role");
		}
		String masked = Masking.email(normalizedEmail);
		StaffUser user = existing.orElseGet(() -> staff.save(new StaffUser(cipher.encrypt(normalizedEmail),
				emailHash(normalizedEmail), masked, displayName == null || displayName.isBlank() ? masked : displayName)));
		user.update(displayName, mobile == null ? null : index.hash("staff_mobile", Mobiles.normalize(mobile)), roles,
				active);
		audit.record(new AuditEntry("STAFF_ACCOUNT_SAVED", "SYSTEM", null, "STAFF", user.getId().toString(),
				Map.of("roles", user.getRoles().toString(), "active", Boolean.toString(active))));
		return user.view();
	}

	@Override
	@Transactional(readOnly = true)
	public Optional<StaffView> find(UUID staffId) {
		return staff.findById(staffId).map(StaffUser::view);
	}

	/** Roles are read on every request, so a removed role or a disabled account counts at once. */
	@Transactional(readOnly = true)
	Optional<Set<Role>> activeRoles(UUID staffId) {
		return staff.findById(staffId).filter(StaffUser::isActive).map(StaffUser::getRoles);
	}

	@Override
	public List<String> productionViolations() {
		List<String> violations = new ArrayList<>();
		if (properties.otpLoginEnabled()) {
			violations.add("eloka.staff.otp-login-enabled=true lets staff sign in without Google");
		}
		return violations;
	}

	private SignIn open(StaffUser user, String method) {
		TokenPair tokens = sessions.open(SessionKind.STAFF, user.getId());
		audit.record(new AuditEntry("STAFF_SIGNED_IN", "STAFF", user.getId().toString(), "STAFF", user.getId().toString(),
				Map.of("method", method)));
		return new SignIn.SignedIn(tokens, user.view());
	}

	/** Every refused Google sign-in is audited, never with the email. */
	private SignIn refused(String staffId, String reason) {
		audit.record(new AuditEntry("STAFF_SIGN_IN_REFUSED", "ANONYMOUS", null, "STAFF", staffId,
				Map.of("method", "GOOGLE", "reason", reason)));
		return new SignIn.NotAllowed();
	}

	private byte[] emailHash(String normalizedEmail) {
		return index.hash("staff_email", normalizedEmail);
	}

	private void requireOtpSignIn() {
		if (!properties.otpLoginEnabled()) {
			throw new NotFoundException("NOT_FOUND", "Not found");
		}
	}
}
