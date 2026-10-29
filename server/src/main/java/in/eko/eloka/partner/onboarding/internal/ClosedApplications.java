package in.eko.eloka.partner.onboarding.internal;

import in.eko.eloka.partner.onboarding.ApplicationClosed;
import in.eko.eloka.partner.verification.VerificationApi;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * What a closed application (withdrawn, expired, rejected, approved, ineligible) lets go of, in the
 * same transaction as the status change. Nothing is deleted: records, files and the audit trail stay
 * until a retention policy says otherwise (docs/OPEN_QUESTIONS.md Q-5).
 */
@Component
class ClosedApplications {

	private final VerificationApi verification;

	ClosedApplications(VerificationApi verification) {
		this.verification = verification;
	}

	/** An unfinished DigiLocker trip can no longer complete for a closed application. */
	@EventListener
	void on(ApplicationClosed closed) {
		verification.endDigiLockerTrips(closed.applicationId());
	}
}
