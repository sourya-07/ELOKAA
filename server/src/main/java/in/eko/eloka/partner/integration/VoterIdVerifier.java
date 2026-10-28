package in.eko.eloka.partner.integration;

/** Looks up a voter ID (EPIC) in the electoral roll (EPS Voter ID API). */
public interface VoterIdVerifier {

	/**
	 * @throws ProviderNotConfiguredException when no provider is configured
	 * @throws ProviderUnavailableException when the provider or its source cannot answer now
	 */
	IdDocumentCheck verify(String epicNumber);
}
