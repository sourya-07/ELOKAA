package in.eko.eloka.partner.verification.internal;

import in.eko.eloka.partner.shared.error.RateLimitedException;
import in.eko.eloka.partner.integration.ProviderUnavailableException;
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

	List<DigiLockerSession> findByApplicationIdAndConsumedAtIsNull(UUID applicationId);

	long countByApplicationIdAndCreatedAtAfter(UUID applicationId, Instant since);
}

@Service
class VerificationService implements VerificationApi {

	private static final Duration DIGILOCKER_WINDOW = Duration.ofMinutes(15);
	/** DigiLocker trips an application may start per hour (each one is a provider session). */
	private static final int MAX_STARTS_PER_HOUR = 10;
	private static final java.util.regex.Pattern PAN = java.util.regex.Pattern.compile("[A-Z]{5}\\d{4}[A-Z]");
	private static final java.util.regex.Pattern EPIC = java.util.regex.Pattern.compile("[A-Z]{3}\\d{7}");
	private static final java.util.regex.Pattern LICENCE = java.util.regex.Pattern.compile("[A-Z]{2}\\d{13}");
	private static final char[] REF_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();
	private static final SecureRandom RANDOM = new SecureRandom();

	private final VerificationRepository verifications;
	private final DigiLockerSessionRepository sessions;
	private final PanVerifier panVerifier;
	private final DigiLockerProvider digiLocker;
	private final AlternateIdChecks alternateIds;
	private final FieldCipher cipher;
	private final BlindIndex index;
	private final Clock clock;

	VerificationService(VerificationRepository verifications, DigiLockerSessionRepository sessions,
			PanVerifier panVerifier, DigiLockerProvider digiLocker, AlternateIdChecks alternateIds, FieldCipher cipher,
			BlindIndex index, Clock clock) {
		this.alternateIds = alternateIds;
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
	public PanOutcome verifyPan(UUID applicationId, String rawPan) {
		String pan = rawPan == null ? "" : rawPan.trim().toUpperCase();
		if (!PAN.matcher(pan).matches()) {
			throw ValidationException.field("pan", "Enter a valid 10-character PAN, for example ABCDE1234F");
		}
		if (pan.charAt(3) != 'P') {
			throw ValidationException.field("pan", "This is not a personal PAN. Use the PAN issued to you as a person.");
		}
		Verification person = latestVerifiedDigiLocker(applicationId).orElseThrow(() -> new ApiProblem(HttpStatus.CONFLICT,
				"AADHAAR_NOT_VERIFIED", "Verify your Aadhaar through DigiLocker first"));
		String name = cipher.decrypt(person.getNameEnc());
		LocalDate dob = LocalDate.parse(cipher.decrypt(person.getDobEnc()));
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
		Instant now = Instant.now(clock);
		if (sessions.countByApplicationIdAndCreatedAtAfter(applicationId, now.minus(Duration.ofHours(1))) >= MAX_STARTS_PER_HOUR) {
			throw new RateLimitedException("DIGILOCKER_LIMIT_REACHED",
					"Too many DigiLocker attempts. Please try again in an hour.", Duration.ofHours(1));
		}
		// "Start again": an earlier, unfinished trip can no longer be completed
		sessions.findByApplicationIdAndConsumedAtIsNull(applicationId).forEach(open -> open.consume(now));
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
		DigiLockerProvider.Fetch fetch;
		try {
			fetch = digiLocker.fetch(session.getVerificationRef(), session.getProviderRef());
		}
		catch (ProviderUnavailableException ex) {
			return new DigiLockerOutcome.Unavailable(); // the session stays open: "Check again" can finish it
		}
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
				if (age(person.dob(), LocalDate.ofInstant(now, java.time.ZoneId.of("Asia/Kolkata"))) < 18) {
					verifications.save(new Verification(applicationId, Kind.AADHAAR, Method.DIGILOCKER,
							EvidenceStatus.VERIFICATION_FAILED, "digilocker", session.getProviderRef()).failed("UNDER_AGE"));
					yield new DigiLockerOutcome.Failed("UNDER_AGE");
				}
				verifications.save(new Verification(applicationId, Kind.AADHAAR, Method.DIGILOCKER, EvidenceStatus.VERIFIED,
						"digilocker", session.getProviderRef())
					.value(null, null, "XXXX-XXXX-" + person.aadhaarLast4())
					.person(cipher.encrypt(person.name()), cipher.encrypt(person.dob().toString())));
				DigiLockerProvider.Address a = person.address();
				Address address = a == null ? new Address(null, null, null, null, null, null)
						: new Address(a.house(), a.street(), a.city(), a.district(), a.state(), a.pincode());
				byte[] identity = index.hash("aadhaar-identity",
						person.name().trim().toUpperCase() + "|" + person.dob() + "|" + person.aadhaarLast4());
				yield new DigiLockerOutcome.Verified(person.aadhaarLast4(), address, person.dob(), identity);
			}
		};
	}

	@Override
	@Transactional
	public AlternateIdOutcome verifyAlternateId(UUID applicationId, AlternateType type, String rawNumber) {
		String number = rawNumber == null ? "" : rawNumber.trim().toUpperCase().replaceAll("[\\s-]", "");
		java.util.regex.Pattern format = type == AlternateType.VOTER_ID ? EPIC : LICENCE;
		if (!format.matcher(number).matches()) {
			throw ValidationException.field("number", type == AlternateType.VOTER_ID
					? "Enter the 10-character voter ID (EPIC) number, for example ABC1234567"
					: "Enter the driving licence number, for example MH0120110012345");
		}
		Verification person = latestVerifiedDigiLocker(applicationId).orElseThrow(() -> new ApiProblem(HttpStatus.CONFLICT,
				"AADHAAR_NOT_VERIFIED", "Verify your Aadhaar through DigiLocker first"));
		String aadhaarName = cipher.decrypt(person.getNameEnc());
		LocalDate aadhaarDob = LocalDate.parse(cipher.decrypt(person.getDobEnc()));
		AlternateIdChecks.Result check = alternateIds.check(type, number, aadhaarName, aadhaarDob);
		if (check.result() == AlternateResult.UNAVAILABLE) {
			return new AlternateIdOutcome(AlternateResult.UNAVAILABLE, null, null);
		}
		String masked = "*".repeat(number.length() - 4) + number.substring(number.length() - 4);
		byte[] hash = index.hash(type.name().toLowerCase(), number);
		Verification attempt = new Verification(applicationId, type.kind(), Method.PROVIDER, EvidenceStatus.VERIFIED,
				type.name().toLowerCase(), check.providerRef())
			.value(cipher.encrypt(number), hash, masked);
		if (check.holderName() != null) {
			attempt.person(cipher.encrypt(check.holderName()), check.holderDob() == null ? null : cipher.encrypt(check.holderDob().toString()));
		}
		if (check.result() != AlternateResult.VERIFIED) {
			attempt.failed(check.result().name());
		}
		verifications.save(attempt);
		return new AlternateIdOutcome(check.result(), masked, check.result() == AlternateResult.VERIFIED ? hash : null);
	}

	@Override
	@Transactional(readOnly = true)
	public Map<AlternateType, Long> alternateIdFailures(UUID applicationId, Instant since) {
		Map<AlternateType, Long> failures = new EnumMap<>(AlternateType.class);
		for (Verification v : verifications.findByApplicationIdOrderByCreatedAtDesc(applicationId)) {
			if (since != null && v.getCreatedAt().isBefore(since)) {
				continue;
			}
			if (v.getStatus() == EvidenceStatus.VERIFICATION_FAILED
					&& (v.getKind() == Kind.VOTER_ID || v.getKind() == Kind.DRIVING_LICENCE)) {
				failures.merge(AlternateType.valueOf(v.getKind().name()), 1L, Long::sum);
			}
		}
		return failures;
	}

	@Override
	@Transactional(readOnly = true)
	public java.util.Set<AlternateType> verifiedAlternateIds(UUID applicationId) {
		java.util.Set<AlternateType> verified = java.util.EnumSet.noneOf(AlternateType.class);
		for (AlternateType type : AlternateType.values()) {
			latest(applicationId, type.kind()).filter(v -> v.getStatus() == EvidenceStatus.VERIFIED)
				.ifPresent(v -> verified.add(type));
		}
		return verified;
	}

	@Override
	@Transactional(readOnly = true)
	public Optional<Identity> verifiedIdentity(UUID applicationId) {
		Optional<Verification> pan = latest(applicationId, Kind.PAN).filter(v -> v.getStatus() == EvidenceStatus.VERIFIED);
		if (pan.isEmpty()) {
			return Optional.empty();
		}
		Verification person = latestVerifiedDigiLocker(applicationId).orElse(pan.get());
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
	@Transactional(readOnly = true)
	public Optional<String> revealPan(UUID applicationId) {
		return latest(applicationId, Kind.PAN).filter(v -> v.getStatus() == EvidenceStatus.VERIFIED)
			.map(v -> cipher.decrypt(v.getValueEnc()));
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

	/**
	 * The DigiLocker identity on record. A later failed DigiLocker retry must not hide it, so those
	 * rows are skipped; a later manual Aadhaar (the applicant switched method) does replace it.
	 */
	private Optional<Verification> latestVerifiedDigiLocker(UUID applicationId) {
		return verifications.findByApplicationIdOrderByCreatedAtDesc(applicationId)
			.stream()
			.filter(v -> v.getKind() == Kind.AADHAAR)
			.filter(v -> v.getMethod() != Method.DIGILOCKER || v.getStatus() == EvidenceStatus.VERIFIED)
			.findFirst()
			.filter(v -> v.getMethod() == Method.DIGILOCKER);
	}

	private Optional<Verification> latest(UUID applicationId, Kind kind) {
		return verifications.findByApplicationIdOrderByCreatedAtDesc(applicationId)
			.stream()
			.filter(v -> v.getKind() == kind)
			.findFirst();
	}

	/** Age on {@code today} in whole years. */
	private static int age(LocalDate dob, LocalDate today) {
		return java.time.Period.between(dob, today).getYears();
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
