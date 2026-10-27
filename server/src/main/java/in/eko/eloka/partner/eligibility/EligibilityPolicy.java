package in.eko.eloka.partner.eligibility;

/**
 * The partner eligibility rule. The threshold is a constant on purpose: changing who may become
 * a partner takes a reviewed code change, not a setting (ADR-0005). There is no override.
 */
public final class EligibilityPolicy {

	/** Lowest CIBIL score that qualifies. */
	public static final int MIN_SCORE = 700;

	/** Scores bureaus report; anything outside is a bad answer, not a score. */
	static final int LOWEST_SCORE = 300;
	static final int HIGHEST_SCORE = 900;

	private EligibilityPolicy() {
	}

	/**
	 * @param score the bureau's score, or {@code null} when it has no record of the person
	 * @return ELIGIBLE only for a valid score of at least {@link #MIN_SCORE}
	 */
	public static EligibilityOutcome decide(Integer score) {
		if (score == null || score < LOWEST_SCORE || score > HIGHEST_SCORE) {
			return EligibilityOutcome.UNABLE_TO_ASSESS;
		}
		return score >= MIN_SCORE ? EligibilityOutcome.ELIGIBLE : EligibilityOutcome.INELIGIBLE;
	}
}
