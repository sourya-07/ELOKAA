package in.eko.eloka.partner.eligibility;

import java.time.LocalDate;
import java.util.UUID;

/** Public API of the eligibility module. */
public interface EligibilityApi {

	/**
	 * @param failureCode why the person could not be assessed ({@code NO_CREDIT_HISTORY},
	 *        {@code BUREAU_UNAVAILABLE}, {@code INVALID_SCORE}); null otherwise
	 * @param reused true when a recent result for the same PAN in this application was reused
	 *        instead of asking the bureau again
	 */
	record Assessment(EligibilityOutcome outcome, String failureCode, boolean reused) {
	}

	/**
	 * Assesses the person behind a verified PAN. A bureau failure is recorded and returned as
	 * {@link EligibilityOutcome#UNABLE_TO_ASSESS}, never thrown as success.
	 *
	 * @throws in.eko.eloka.partner.integration.ProviderNotConfiguredException when no bureau is configured
	 */
	Assessment assess(UUID applicationId, String pan, String name, LocalDate dob);

	/** The latest recorded outcome for the application, {@code PENDING} when none. */
	EligibilityOutcome latestOutcome(UUID applicationId);
}
