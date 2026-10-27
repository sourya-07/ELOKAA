package in.eko.eloka.partner.verification.internal;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import in.eko.eloka.partner.integration.DigiLockerProvider;
import in.eko.eloka.partner.integration.PanVerifier;
import in.eko.eloka.partner.shared.crypto.BlindIndex;
import in.eko.eloka.partner.shared.crypto.FieldCipher;
import in.eko.eloka.partner.shared.crypto.Masking;
import in.eko.eloka.partner.shared.crypto.Tokens;
import in.eko.eloka.partner.shared.error.ApiProblem;
import in.eko.eloka.partner.shared.error.ConflictException;
import in.eko.eloka.partner.shared.error.NotFoundException;
import in.eko.eloka.partner.shared.error.ValidationException;
import in.eko.eloka.partner.verification.EvidenceStatus;
import in.eko.eloka.partner.verification.VerificationApi;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

interface VerificationRepository extends JpaRepository<Verification, UUID> {

	List<Verification> findByApplicationIdOrderByCreatedAtDesc(UUID applicationId);

	Optional<Verification> findByIdAndApplicationId(UUID id, UUID applicationId);
}

interface DigiLockerSessionRepository extends JpaRepository<DigiLockerSession, UUID> {

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select s from DigiLockerSession s where s.stateHash = :stateHash")
	Optional<DigiLockerSession> lockByStateHash(@Param("stateHash") byte[] stateHash);
}

@Service
class VerificationService implements VerificationApi {

	private static final Duration DIGILOCKER_WINDOW = Duration.ofMinutes(15);
	private static final java.util.regex.Pattern PAN = java.util.regex.Pattern.compile("[A-Z]{5}\\d{4}[A-Z]");
	private static final char[] REF_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();
	private static final SecureRandom RANDOM = new SecureRandom();

	private final VerificationRepository verifications;
	private final DigiLockerSessionRepository sessions;
	private final PanVerifier panVerifier;
	private final DigiLockerProvider digiLocker;
	private final FieldCipher cipher;
	private final BlindIndex index;
	private final Clock clock;

	VerificationService(VerificationRepository verifications, DigiLockerSessionRepository sessions,
			PanVerifier panVerifier, DigiLockerProvider digiLocker, FieldCipher cipher, BlindIndex index, Clock clock) {
		this.verifications = verifications;
		this.sessions = sessions;
		this.panVerifier = panVerifier;
		this.digiLocker = digiLocker;
		this.cipher = cipher;
		this.index = index;
		this.clock = clock;
	}

	@Override
	@Transactional
	public PanOutcome verifyPan(UUID applicationId, String rawPan, String typedName, LocalDate typedDob) {
		String pan = rawPan == null ? "" : rawPan.trim().toUpperCase();
		if (!PAN.matcher(pan).matches()) {
			throw ValidationException.field("pan", "Enter a valid 10-character PAN, for example ABCDE1234F");
		}
		if (pan.charAt(3) != 'P') {
			throw ValidationException.field("pan", "This is not a personal PAN. Use the PAN issued to you as a person.");
		}
		Optional<Verification> digiLockerIdentity = latest(applicationId, Kind.AADHAAR)
			.filter(v -> v.getMethod() == Method.DIGILOCKER && v.getStatus() == EvidenceStatus.VERIFIED);
		String name;
		LocalDate dob;
		if (digiLockerIdentity.isPresent()) {
			name = cipher.decrypt(digiLockerIdentity.get().getNameEnc());
			dob = requireDob(LocalDate.parse(cipher.decrypt(digiLockerIdentity.get().getDobEnc())));
		}
		else {
			name = requireName(typedName);
			dob = requireDob(typedDob);
		}
		PanVerifier.PanCheck check = panVerifier.verify(pan, name, dob);
		PanOutcome.Result result = PanOutcome.Result.valueOf(check.result().name());
		byte[] hash = index.hash("pan", pan);
		Verification attempt = new Verification(applicationId, Kind.PAN, Method.PROVIDER, EvidenceStatus.VERIFIED,
				"pan", check.providerRef())
			.value(cipher.encrypt(pan), hash, Masking.pan(pan))
			.person(cipher.encrypt(name), cipher.encrypt(dob.toString()));
		if (result != PanOutcome.Result.MATCH) {
			attempt.failed(result.name());
		}
		verifications.save(attempt);
		return new PanOutcome(result, Masking.pan(pan), result == PanOutcome.Result.MATCH ? hash : null);
	}

	@Override
	@Transactional
	public DigiLockerStart startDigiLocker(UUID applicationId, String returnUrlPattern) {
		String state = Tokens.random("dls_");
		String verificationRef = verificationRef();
		DigiLockerProvider.Session session = digiLocker.start(verificationRef, returnUrlPattern.replace("{state}", state));
		sessions.save(new DigiLockerSession(applicationId, verificationRef, session.referenceId(), Tokens.sha256(state),
				Instant.now(clock).plus(DIGILOCKER_WINDOW)));
		return new DigiLockerStart(session.url());
	}

	@Override
	@Transactional
	public DigiLockerOutcome completeDigiLocker(UUID applicationId, String state) {
		if (state == null || !state.startsWith("dls_")) {
			throw ValidationException.field("state", "This DigiLocker link is not valid. Please start again.");
		}
		Instant now = Instant.now(clock);
		DigiLockerSession session = sessions.lockByStateHash(Tokens.sha256(state))
			.filter(s -> s.getApplicationId().equals(applicationId))
			.orElseThrow(() -> ValidationException.field("state", "This DigiLocker link is not valid. Please start again."));
		if (session.isConsumed()) {
			throw new ConflictException("DIGILOCKER_ALREADY_USED", "This DigiLocker link was already used.");
		}
		if (session.isExpired(now)) {
			throw new ApiProblem(HttpStatus.GONE, "DIGILOCKER_EXPIRED", "This DigiLocker link has expired. Please start again.");
		}
		DigiLockerProvider.Fetch fetch = digiLocker.fetch(session.getVerificationRef(), session.getProviderRef());
		return switch (fetch) {
			case DigiLockerProvider.Fetch.Pending pending -> new DigiLockerOutcome.Pending();
			case DigiLockerProvider.Fetch.Failed failed -> {
				session.consume(now);
				verifications.save(new Verification(applicationId, Kind.AADHAAR, Method.DIGILOCKER,
						EvidenceStatus.VERIFICATION_FAILED, "digilocker", session.getProviderRef()).failed(failed.code()));
				yield new DigiLockerOutcome.Failed(failed.code());
			}
			case DigiLockerProvider.Fetch.Done done -> {
				session.consume(now);
				DigiLockerProvider.Identity person = done.identity();
				verifications.save(new Verification(applicationId, Kind.AADHAAR, Method.DIGILOCKER, EvidenceStatus.VERIFIED,
						"digilocker", session.getProviderRef())
					.value(null, null, "XXXX-XXXX-" + person.aadhaarLast4())
					.person(cipher.encrypt(person.name()), cipher.encrypt(person.dob().toString())));
				DigiLockerProvider.Address a = person.address();
				Address address = a == null ? new Address(null, null, null, null, null, null)
						: new Address(a.house(), a.street(), a.city(), a.district(), a.state(), a.pincode());
				yield new DigiLockerOutcome.Verified(person.aadhaarLast4(), address);
			}
		};
	}

	@Override
	@Transactional
	public void recordManualAadhaar(UUID applicationId, String aadhaarLast4) {
		if (aadhaarLast4 == null || !aadhaarLast4.trim().matches("\\d{4}")) {
			throw ValidationException.field("aadhaar_last4", "Enter the last 4 digits of your Aadhaar number");
		}
		verifications.save(new Verification(applicationId, Kind.AADHAAR, Method.MANUAL,
				EvidenceStatus.MANUAL_REVIEW_REQUIRED, null, null)
			.value(null, null, "XXXX-XXXX-" + aadhaarLast4.trim()));
	}

	@Override
	@Transactional(readOnly = true)
	public Optional<Identity> verifiedIdentity(UUID applicationId) {
		Optional<Verification> pan = latest(applicationId, Kind.PAN).filter(v -> v.getStatus() == EvidenceStatus.VERIFIED);
		if (pan.isEmpty()) {
			return Optional.empty();
		}
		Verification person = latest(applicationId, Kind.AADHAAR)
			.filter(v -> v.getMethod() == Method.DIGILOCKER && v.getStatus() == EvidenceStatus.VERIFIED)
			.orElse(pan.get());
		return Optional.of(new Identity(cipher.decrypt(person.getNameEnc()),
				LocalDate.parse(cipher.decrypt(person.getDobEnc())), cipher.decrypt(pan.get().getValueEnc())));
	}

	@Override
	@Transactional(readOnly = true)
	public List<Evidence> evidence(UUID applicationId) {
		Map<Kind, Evidence> latestByKind = new EnumMap<>(Kind.class);
		for (Verification v : verifications.findByApplicationIdOrderByCreatedAtDesc(applicationId)) {
			latestByKind.putIfAbsent(v.getKind(), v.toEvidence());
		}
		return List.copyOf(latestByKind.values());
	}

	@Override
	@Transactional
	public void decide(UUID applicationId, UUID evidenceId, boolean verified, String reasonCode) {
		Verification v = verifications.findByIdAndApplicationId(evidenceId, applicationId)
			.orElseThrow(() -> new NotFoundException("EVIDENCE_NOT_FOUND", "Check not found"));
		if (v.getStatus() == EvidenceStatus.VERIFICATION_FAILED || v.getStatus() == EvidenceStatus.PENDING) {
			throw new ConflictException("EVIDENCE_NOT_DECIDABLE", "This check cannot be decided by hand");
		}
		v.decide(verified, reasonCode, Instant.now(clock));
	}

	private Optional<Verification> latest(UUID applicationId, Kind kind) {
		return verifications.findByApplicationIdOrderByCreatedAtDesc(applicationId)
			.stream()
			.filter(v -> v.getKind() == kind)
			.findFirst();
	}

	private static String requireName(String name) {
		String value = name == null ? "" : name.trim().replaceAll("\\s+", " ");
		if (value.length() < 2 || value.length() > 100 || !value.matches("[A-Za-z .'-]+")) {
			throw ValidationException.field("name", "Enter your name exactly as it is on your PAN card");
		}
		return value.toUpperCase();
	}

	private LocalDate requireDob(LocalDate dob) {
		LocalDate today = LocalDate.now(clock);
		if (dob == null || dob.isAfter(today.minusYears(18)) || dob.isBefore(today.minusYears(100))) {
			throw ValidationException.field("dob", "Enter your date of birth. You must be at least 18.");
		}
		return dob;
	}

	/** Our id for a DigiLocker attempt: "DL" + 18 characters, within EPS's 20-character limit. */
	private static String verificationRef() {
		StringBuilder ref = new StringBuilder("DL");
		for (int i = 0; i < 18; i++) {
			ref.append(REF_ALPHABET[RANDOM.nextInt(REF_ALPHABET.length)]);
		}
		return ref.toString();
	}
}
