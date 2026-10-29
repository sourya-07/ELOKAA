package in.eko.eloka.partner.onboarding.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import in.eko.eloka.partner.shared.error.ApiProblem;
import org.junit.jupiter.api.Test;

/** The live photo's location: required, real, recent and precise enough; doubtful ones are flagged. */
class LocationRulesTest {

	private static final Instant NOW = Instant.parse("2026-10-28T10:00:00Z");
	private final LocationRules rules = new LocationRules(1000, 100);

	@Test
	void aGoodLocationInIndiaHasNoFlags() {
		LocationRules.Checked checked = rules.check(new LocationRules.Input("19.0596", "72.8295", "18", NOW.toString()), NOW);
		assertThat(checked.flags()).isEmpty();
		assertThat(checked.accuracyM()).isEqualTo(18);
	}

	@Test
	void doubtfulLocationsAreKeptButFlagged() {
		assertThat(rules.check(new LocationRules.Input("28.61", "77.21", "100.5", NOW.toString()), NOW).flags()).isEqualTo("LOW_ACCURACY");
		assertThat(rules.check(new LocationRules.Input("1.35", "103.82", "20", NOW.toString()), NOW).flags()).isEqualTo("OUTSIDE_INDIA");
	}

	@Test
	void missingInventedRoughOrOldLocationsAreRefused() {
		assertCode(null, "LOCATION_REQUIRED");
		assertCode(new LocationRules.Input(null, "72.8", "10", null), "LOCATION_REQUIRED");
		// without a capture time the "taken just now" check could not run
		assertCode(new LocationRules.Input("19.0", "72.8", "10", null), "LOCATION_REQUIRED");
		assertCode(new LocationRules.Input("0", "0", "10", NOW.toString()), "LOCATION_INVALID");
		assertCode(new LocationRules.Input("91", "72.8", "10", NOW.toString()), "LOCATION_INVALID");
		assertCode(new LocationRules.Input("NaN", "72.8", "10", NOW.toString()), "LOCATION_INVALID");
		assertCode(new LocationRules.Input("19.0", "abc", "10", NOW.toString()), "LOCATION_INVALID");
		assertCode(new LocationRules.Input("19.0", "72.8", "0", NOW.toString()), "LOCATION_INVALID");
		assertCode(new LocationRules.Input("19.0", "72.8", "1500", NOW.toString()), "LOCATION_TOO_INACCURATE");
		assertCode(new LocationRules.Input("19.0", "72.8", "10", NOW.minusSeconds(600).toString()), "LOCATION_STALE");
		assertCode(new LocationRules.Input("19.0", "72.8", "10", "yesterday"), "LOCATION_INVALID");
	}

	private void assertCode(LocationRules.Input input, String code) {
		assertThatThrownBy(() -> rules.check(input, NOW)).isInstanceOf(ApiProblem.class)
			.satisfies(e -> assertThat(((ApiProblem) e).getCode()).isEqualTo(code));
	}
}
