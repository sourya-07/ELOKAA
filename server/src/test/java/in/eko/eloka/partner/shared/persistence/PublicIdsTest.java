package in.eko.eloka.partner.shared.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class PublicIdsTest {

	@Test
	void prefixedCrockfordAndTimeOrdered() {
		String earlier = PublicIds.next("ptn", Instant.parse("2026-10-16T10:00:00Z"));
		String later = PublicIds.next("ptn", Instant.parse("2026-10-16T10:00:01Z"));
		assertThat(earlier).matches("ptn_[0-9A-HJKMNP-TV-Z]{26}");
		assertThat(earlier.substring(0, 14)).isLessThan(later.substring(0, 14));
		assertThat(PublicIds.next("doc", Instant.now())).isNotEqualTo(PublicIds.next("doc", Instant.now()));
	}
}
