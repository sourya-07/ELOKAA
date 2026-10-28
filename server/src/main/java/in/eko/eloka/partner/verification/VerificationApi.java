package in.eko.eloka.partner.verification;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Public API of the verification module. Callers hold the application's row lock. */
public interface VerificationApi {

	enum Kind {
		PAN,
		AADHAAR,
		VOTER_ID,
		DRIVING_LICENCE
	}

	/** The second identity document: a voter ID or a driving licence, checked with its government record. */
	enum AlternateType {
		VOTER_ID,
		DRIVING_LICENCE;

		public Kind kind() {
			return Kind.valueOf(name());
		}
	}

	/**
	 * @param numberMasked for display; @param numberHash blind index (one live application per document),
	 *        only when verified
	 */
	record AlternateIdOutcome(AlternateResult result, String numberMasked, byte[] numberHash) {
	}

	enum AlternateResult {
		VERIFIED,
		NOT_FOUND,
		NAME_MISMATCH,
		DOB_MISMATCH,
		NOT_ACTIVE,
		/** The provider could not answer now: not a result, nothing recorded */
		UNAVAILABLE
	}

	enum Method {
		PROVIDER,
		DIGILOCKER,
		MANUAL
	}

	/** Outcome of a PAN check. {@code panHash} is the blind index, set only on {@link PanOutcome.Result#MATCH}. */
	record PanOutcome(Result result, String panMasked, byte[] panHash) {

		public enum Result {
			MATCH,
			NAME_MISMATCH,
			DOB_MISMATCH,
			INVALID_PAN
		}
	}

	record Address(String line1, String line2, String city, String district, String state, String pincode) {
	}

	/** A started DigiLocker session: send the applicant to {@code url}. */
	record DigiLockerStart(String url) {
	}

	/** What a finished DigiLocker session gave. */
	sealed interface DigiLockerOutcome {

		record Pending() implements DigiLockerOutcome {
		}

		/**
		 * @param address may have missing parts; the applicant then completes it by hand
		 * @param dob date of birth from DigiLocker (age rules)
		 * @param identityHash blind index of name, date of birth and Aadhaar last 4: a redo with the
		 *        same identity does not make the later steps stale
		 */
		record Verified(String aadhaarLast4, Address address, java.time.LocalDate dob, byte[] identityHash)
				implements DigiLockerOutcome {
		}

		/**
		 * A real answer that is not a pass: {@code NO_AADHAAR_DATA} (DigiLocker has no Aadhaar for this
		 * person), {@code INVALID_REFERENCE}, {@code UNDER_AGE}.
		 */
		record Failed(String code) implements DigiLockerOutcome {
		}

		/** DigiLocker or EPS could not answer now: not a result. The session stays open to be checked again. */
		record Unavailable() implements DigiLockerOutcome {
		}
	}

	/** Name and date of birth the identity checks established; for the credit check only. */
	record Identity(String name, LocalDate dob, String pan) {
	}

	/** One check, as staff and the applicant summary see it (masked values only). */
	record Evidence(UUID id, Kind kind, Method method, EvidenceStatus status, String valueMasked, String failureCode) {
	}

	/**
	 * Checks a PAN with the provider and records the attempt.
	 *
	 * @param name for the step-by-step path the name typed by the applicant; for DigiLocker the
	 *        name DigiLocker gave is used instead and this is ignored
	 */
	/**
	 * Checks the PAN against the name and date of birth DigiLocker gave.
	 *
	 * @throws in.eko.eloka.partner.shared.error.ApiProblem {@code AADHAAR_NOT_VERIFIED} without a verified DigiLocker identity
	 */
	PanOutcome verifyPan(UUID applicationId, String pan);

	/** @param returnUrlPattern client URL with {@code {state}} where the single-use state goes */
	DigiLockerStart startDigiLocker(UUID applicationId, String returnUrlPattern);

	/** Reads the session the state belongs to. A state works once, for its own application, for 15 minutes. */
	DigiLockerOutcome completeDigiLocker(UUID applicationId, String state);

	/**
	 * Checks a voter ID or driving licence with its government record, and the holder's name (and
	 * date of birth where given) against the DigiLocker identity. Records the attempt, except an outage.
	 *
	 * @throws in.eko.eloka.partner.shared.error.ValidationException for a number in the wrong format
	 * @throws in.eko.eloka.partner.shared.error.ApiProblem {@code AADHAAR_NOT_VERIFIED} without a DigiLocker identity
	 */
	AlternateIdOutcome verifyAlternateId(UUID applicationId, AlternateType type, String number);

	/** Failed record checks per document type since {@code since} (all when null); outages are not counted. */
	java.util.Map<AlternateType, Long> alternateIdFailures(UUID applicationId, java.time.Instant since);

	/** The document types verified for this application (the uploaded images must be of one of them). */
	java.util.Set<AlternateType> verifiedAlternateIds(UUID applicationId);

	/** Name and DOB from DigiLocker if used, else from the PAN step; and the verified PAN. */
	Optional<Identity> verifiedIdentity(UUID applicationId);

	/** The latest attempt per kind. */
	List<Evidence> evidence(UUID applicationId);

	/** The verified PAN in full, for an audited staff reveal. */
	Optional<String> revealPan(UUID applicationId);

	/** Staff decision on a check that needs a person. */
	void decide(UUID applicationId, UUID evidenceId, boolean verified, String reasonCode);
}
