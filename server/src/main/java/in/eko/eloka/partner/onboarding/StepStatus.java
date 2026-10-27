package in.eko.eloka.partner.onboarding;

/** Progress of one step of an application. */
public enum StepStatus {
	NOT_STARTED,
	IN_PROGRESS,
	PENDING_EXTERNAL,
	COMPLETED,
	FAILED,
	BLOCKED,
	LOCKED,
	REOPENED,
	NOT_APPLICABLE;

	/** Counts as done for prerequisites and for submitting the application. */
	public boolean isSatisfied() {
		return this == COMPLETED || this == LOCKED || this == NOT_APPLICABLE;
	}
}
