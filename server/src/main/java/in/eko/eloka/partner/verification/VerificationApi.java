package in.eko.eloka.partner.verification;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Public API of the verification module. Callers hold the application's row lock. */
public interface VerificationApi {

	enum Kind {
		PAN,
		AADHAAR
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

		/** @param address may have missing parts; the applicant then completes it by hand */
		/**
		 * @param dob date of birth from DigiLocker (age rules)
		 * @param identityHash blind index of name, date of birth and Aadhaar last 4: a redo with the
		 *        same identity does not make the later steps stale
		 */
		record Verified(String aadhaarLast4, Address address, java.time.LocalDate dob, byte[] identityHash)
				implements DigiLockerOutcome {
		}

		record Failed(String code) implements DigiLockerOutcome {
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
	PanOutcome verifyPan(UUID applicationId, String pan, String name, LocalDate dob);

	/** @param returnUrlPattern client URL with {@code {state}} where the single-use state goes */
	DigiLockerStart startDigiLocker(UUID applicationId, String returnUrlPattern);

	/** Reads the session the state belongs to. A state works once, for its own application, for 15 minutes. */
	DigiLockerOutcome completeDigiLocker(UUID applicationId, String state);

	/** Records a manual Aadhaar (masked images checked by staff later). */
	void recordManualAadhaar(UUID applicationId, String aadhaarLast4);

	/** Name and DOB from DigiLocker if used, else from the PAN step; and the verified PAN. */
	Optional<Identity> verifiedIdentity(UUID applicationId);

	/** The latest attempt per kind. */
	List<Evidence> evidence(UUID applicationId);

	/** The verified PAN in full, for an audited staff reveal. */
	Optional<String> revealPan(UUID applicationId);

	/** Staff decision on a check that needs a person. */
	void decide(UUID applicationId, UUID evidenceId, boolean verified, String reasonCode);
}
