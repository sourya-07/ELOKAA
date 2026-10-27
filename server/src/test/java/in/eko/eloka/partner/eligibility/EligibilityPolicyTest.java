package in.eko.eloka.partner.eligibility;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** The 700 rule at its edges (ADR-0005). */
class EligibilityPolicyTest {

	@ParameterizedTest(name = "{0} -> {1}")
	@CsvSource(nullValues = "null", value = { "null, UNABLE_TO_ASSESS", "299, UNABLE_TO_ASSESS", "300, INELIGIBLE",
			"699, INELIGIBLE", "700, ELIGIBLE", "701, ELIGIBLE", "900, ELIGIBLE", "901, UNABLE_TO_ASSESS",
			"-1, UNABLE_TO_ASSESS" })
	void decides(Integer score, EligibilityOutcome expected) {
		assertThat(EligibilityPolicy.decide(score)).isEqualTo(expected);
	}

	@Test
	void thresholdIsSevenHundred() {
		assertThat(EligibilityPolicy.MIN_SCORE).isEqualTo(700);
	}
}
