package in.eko.eloka.partner.identity.internal;

import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;
import in.eko.eloka.partner.audit.AuditEntry;
import in.eko.eloka.partner.audit.AuditLog;
import in.eko.eloka.partner.shared.crypto.BlindIndex;
import in.eko.eloka.partner.shared.crypto.FieldCipher;
import in.eko.eloka.partner.shared.crypto.Masking;
import in.eko.eloka.partner.shared.security.SessionKind;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Applicant sign-in: verify the OTP, find or create the applicant for that mobile, open a
 * session. The first verified OTP creates the applicant; later ones resume it.
 */
@Service
class ApplicantSignIn {

	/** Result of a verification attempt. */
	sealed interface Outcome {

		record SignedIn(TokenPair tokens) implements Outcome {
		}

		record WrongCode(int attemptsLeft) implements Outcome {
		}

		record Expired() implements Outcome {
		}
	}

	private final OtpService otps;
	private final SessionService sessions;
	private final ApplicantRepository applicants;
	private final FieldCipher cipher;
	private final BlindIndex index;
	private final AuditLog audit;
	private final TransactionTemplate transactions;

	ApplicantSignIn(OtpService otps, SessionService sessions, ApplicantRepository applicants, FieldCipher cipher,
			BlindIndex index, AuditLog audit, TransactionTemplate transactions) {
		this.otps = otps;
		this.sessions = sessions;
		this.applicants = applicants;
		this.cipher = cipher;
		this.index = index;
		this.audit = audit;
		this.transactions = transactions;
	}

	void sendOtp(String mobile, String clientIp) {
		otps.issue(OtpChallenge.PURPOSE_APPLICANT_SIGN_IN, mobile, clientIp);
	}

	Outcome verify(String mobile, String code) {
		OtpService.Check check = otps.check(OtpChallenge.PURPOSE_APPLICANT_SIGN_IN, mobile, code);
		if (check instanceof OtpService.Check.NoLiveOtp) {
			return new Outcome.Expired();
		}
		if (check instanceof OtpService.Check.Wrong wrong) {
			if (wrong.attemptsLeft() == 0) {
				// Pseudonymous target: a prefix of the mobile's blind index, never the number itself
				String target = HexFormat.of().formatHex(index.hash("mobile", mobile), 0, 8);
				transactions.executeWithoutResult(tx -> audit.record(new AuditEntry("APPLICANT_OTP_LOCKED", "ANONYMOUS",
						null, "MOBILE_INDEX", target, Map.of())));
			}
			return new Outcome.WrongCode(wrong.attemptsLeft());
		}
		UUID applicantId = findOrCreate(mobile);
		return transactions.execute(tx -> {
			TokenPair tokens = sessions.open(SessionKind.PARTNER_APPLICANT, applicantId);
			audit.record(new AuditEntry("APPLICANT_SIGNED_IN", "APPLICANT", applicantId.toString(), "APPLICANT",
					applicantId.toString(), Map.of()));
			return new Outcome.SignedIn(tokens);
		});
	}

	@Transactional
	void signOut(UUID sessionId, UUID applicantId) {
		sessions.revoke(sessionId, "SIGN_OUT");
		audit.record(new AuditEntry("APPLICANT_SIGNED_OUT", "APPLICANT", applicantId.toString(), "APPLICANT",
				applicantId.toString(), Map.of()));
	}

	/** Two first sign-ins for one mobile at the same moment: the unique index lets one win. */
	private UUID findOrCreate(String mobile) {
		byte[] hash = index.hash("mobile", mobile);
		try {
			return transactions.execute(tx -> applicants.findByMobileHash(hash)
				.orElseGet(() -> applicants.saveAndFlush(new Applicant(cipher.encrypt(mobile), hash, Masking.mobile(mobile))))
				.getId());
		}
		catch (DataIntegrityViolationException raced) {
			return applicants.findByMobileHash(hash).orElseThrow().getId();
		}
	}
}
