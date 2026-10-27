package in.eko.eloka.partner.integration;

import java.time.LocalDate;

/** Checks a PAN against the Income Tax records together with the holder's name and date of birth. */
public interface PanVerifier {

	enum Result {
		/** Valid PAN, name and date of birth match. */
		MATCH,
		NAME_MISMATCH,
		DOB_MISMATCH,
		/** Not found, fake, deleted or deactivated. */
		INVALID_PAN
	}

	/** @param providerRef the provider's id for this check, for support and audit */
	record PanCheck(Result result, boolean aadhaarSeeded, String providerRef) {
	}

	/**
	 * @throws ProviderNotConfiguredException when no provider is configured
	 * @throws ProviderUnavailableException when the provider fails or cannot say whether the details match
	 */
	PanCheck verify(String pan, String name, LocalDate dob);
}
