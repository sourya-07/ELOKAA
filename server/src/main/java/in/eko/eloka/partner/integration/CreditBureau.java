package in.eko.eloka.partner.integration;

import java.time.LocalDate;

/**
 * Asks a credit bureau for a person's credit score (CIBIL). Only the score is returned; the
 * report itself never enters the system (ADR-0005). No real provider is contracted yet
 * (docs/OPEN_QUESTIONS.md Q-1).
 */
public interface CreditBureau {

	record Request(String pan, String name, LocalDate dob) {
	}

	/**
	 * @param score the bureau score, or {@code null} when the bureau has no record of the person
	 * @param providerRef the bureau's id for this enquiry
	 */
	record Report(Integer score, String providerRef) {

		public boolean noHit() {
			return score == null;
		}
	}

	/**
	 * @throws ProviderNotConfiguredException when no bureau is configured
	 * @throws ProviderUnavailableException on timeouts or an answer we cannot trust
	 */
	Report fetchScore(Request request);
}
