package in.eko.eloka.partner.onboarding.internal;

/** Events that move an application between statuses (docs/PARTNER_JOURNEY.md, "Transitions"). */
enum LifecycleEvent {
	/** A verified credit score below the threshold. */
	MARK_INELIGIBLE,
	/** The applicant finished (or resubmitted after corrections). */
	SUBMIT,
	/** The applicant withdraws. */
	WITHDRAW,
	/** No applicant activity within the idle window, or a correction overdue. */
	EXPIRE,
	/** A reviewer takes the case. */
	CLAIM,
	/** The reviewer gives the case back to the queue. */
	RELEASE,
	/** The reviewer reopens steps for the applicant. */
	REQUEST_CORRECTION,
	/** The approver approves. */
	APPROVE,
	REJECT,
	HOLD,
	/** Hold released: back to review. */
	RELEASE_HOLD
}
