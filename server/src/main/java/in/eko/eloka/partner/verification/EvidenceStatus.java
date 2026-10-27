package in.eko.eloka.partner.verification;

/**
 * Status of one piece of evidence (a check or an uploaded document), separate from the step
 * status (docs/PARTNER_JOURNEY.md, "Evidence status"). Submitting never makes anything verified.
 */
public enum EvidenceStatus {
	NOT_STARTED,
	PENDING,
	SUBMITTED,
	VERIFICATION_IN_PROGRESS,
	VERIFIED,
	REJECTED,
	VERIFICATION_FAILED,
	MANUAL_REVIEW_REQUIRED;

	/** Lets the applicant move on: checked by a provider, or waiting for a person to check it. */
	public boolean allowsProgress() {
		return this == VERIFIED || this == SUBMITTED || this == MANUAL_REVIEW_REQUIRED;
	}
}
