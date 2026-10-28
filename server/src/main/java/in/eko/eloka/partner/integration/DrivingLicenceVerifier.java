package in.eko.eloka.partner.integration;

import java.time.LocalDate;

/** Looks up a driving licence with the transport authority (EPS Driving Licence API). */
public interface DrivingLicenceVerifier {

	/**
	 * @param dob the holder's date of birth; the authority checks it against the licence
	 * @throws ProviderNotConfiguredException when no provider is configured
	 * @throws ProviderUnavailableException when the provider or its source cannot answer now
	 */
	IdDocumentCheck verify(String licenceNumber, LocalDate dob);
}
