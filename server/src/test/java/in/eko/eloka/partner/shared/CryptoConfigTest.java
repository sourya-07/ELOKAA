package in.eko.eloka.partner.shared;

import static org.assertj.core.api.Assertions.assertThat;

import in.eko.eloka.partner.shared.crypto.CryptoConfig;
import in.eko.eloka.partner.shared.crypto.CryptoProperties;
import org.junit.jupiter.api.Test;

class CryptoConfigTest {

	private static final String LOCAL_FIELD_KEY = "cGFydG5lci1sb2NhbC1maWVsZC1rZXktMzItYnl0ZXM=";
	private static final String TEST_INDEX_KEY = "cGFydG5lci10ZXN0LWluZGV4LWtleS0zMi1ieXRlcyE=";
	private static final String FRESH_KEY = "c29tZS1yZWFsLWxvb2tpbmcta2V5LTMyLWJ5dGVzISE=";

	@Test
	void publishedKeysAreViolationsEvenWithExtraWhitespace() {
		CryptoConfig config = new CryptoConfig(new CryptoProperties(" " + LOCAL_FIELD_KEY + "\n", TEST_INDEX_KEY));
		assertThat(config.productionViolations()).hasSize(2);
	}

	@Test
	void freshKeysAreFine() {
		assertThat(new CryptoConfig(new CryptoProperties(FRESH_KEY, FRESH_KEY)).productionViolations()).isEmpty();
	}
}
