package in.eko.eloka.partner.integration;

import java.time.LocalDate;

/**
 * Consent-based Aadhaar retrieval through DigiLocker: send the person to {@link Session#url()},
 * they come back to our return URL, then {@link #fetch} reads what DigiLocker shared. Only the
 * last 4 digits of the Aadhaar number ever reach us.
 */
public interface DigiLockerProvider {

	/** @param referenceId the provider's id for this session */
	record Session(String referenceId, String url) {
	}

	record Address(String house, String street, String city, String district, String state, String pincode) {
	}

	record Identity(String name, LocalDate dob, String aadhaarLast4, Address address) {
	}

	/** Result of reading a session. */
	sealed interface Fetch {

		/** The person has not finished on DigiLocker yet. */
		record Pending() implements Fetch {
		}

		record Done(Identity identity) implements Fetch {
		}

		/** The session cannot give a result (unknown, refused or expired); start a new one. */
		record Failed(String code) implements Fetch {
		}
	}

	/**
	 * @param verificationId our id for this attempt (at most 20 characters)
	 * @param returnUrl where DigiLocker sends the person back
	 */
	Session start(String verificationId, String returnUrl);

	Fetch fetch(String verificationId, String referenceId);
}
