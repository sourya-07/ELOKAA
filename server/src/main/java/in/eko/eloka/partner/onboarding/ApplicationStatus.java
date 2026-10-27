package in.eko.eloka.partner.onboarding;

import java.util.Set;

/** Lifecycle of a partner application (docs/PARTNER_JOURNEY.md, "Application status"). */
public enum ApplicationStatus {
	IN_PROGRESS,
	INELIGIBLE,
	SUBMITTED,
	UNDER_REVIEW,
	CORRECTION_REQUIRED,
	ON_HOLD,
	APPROVED,
	REJECTED,
	WITHDRAWN,
	EXPIRED;

	private static final Set<ApplicationStatus> TERMINAL = Set.of(INELIGIBLE, APPROVED, REJECTED, WITHDRAWN, EXPIRED);

	/** No further transitions. */
	public boolean isTerminal() {
		return TERMINAL.contains(this);
	}

	/** The applicant may change step data. */
	public boolean isEditableByApplicant() {
		return this == IN_PROGRESS || this == CORRECTION_REQUIRED;
	}

	/**
	 * Stops the same person (mobile or PAN) from starting another application. Open applications
	 * block; so do approved ones and, until reapply rules are decided (docs/OPEN_QUESTIONS.md Q-6),
	 * ineligible ones, so a failed credit check cannot be retried with a new application.
	 */
	public boolean blocksNewApplication() {
		return !isTerminal() || this == APPROVED || this == INELIGIBLE;
	}
}
