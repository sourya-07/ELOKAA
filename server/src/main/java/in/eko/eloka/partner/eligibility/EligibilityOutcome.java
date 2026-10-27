package in.eko.eloka.partner.eligibility;

/** Result of a credit assessment (docs/PARTNER_JOURNEY.md, "Eligibility"). */
public enum EligibilityOutcome {
	/** Not assessed yet. */
	PENDING,
	/** Verified score at or above the threshold. */
	ELIGIBLE,
	/** Verified score below the threshold. */
	INELIGIBLE,
	/** The bureau failed, answered badly or has no record of the person. Never counts as eligible. */
	UNABLE_TO_ASSESS
}
