package in.eko.eloka.partner.shared.crypto;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import in.eko.eloka.partner.shared.config.SafetyCheck;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Creates the crypto helpers and refuses the published development keys outside local/test. */
@Configuration
public class CryptoConfig implements SafetyCheck {

	/** The keys committed in application-local.yaml and application-test.yaml. Never valid outside a laptop. */
	static final List<String> PUBLISHED_KEYS = List.of(
			"cGFydG5lci1sb2NhbC1maWVsZC1rZXktMzItYnl0ZXM=",
			"cGFydG5lci1sb2NhbC1pbmRleC1rZXktMzItYnl0ZXM=",
			"cGFydG5lci10ZXN0LWZpZWxkLWtleS0zMi1ieXRlcyE=",
			"cGFydG5lci10ZXN0LWluZGV4LWtleS0zMi1ieXRlcyE=");

	private final CryptoProperties properties;

	public CryptoConfig(CryptoProperties properties) {
		this.properties = properties;
	}

	@Bean
	FieldCipher fieldCipher() {
		return new FieldCipher(properties.fieldKey());
	}

	@Bean
	BlindIndex blindIndex() {
		return new BlindIndex(properties.indexKey());
	}

	@Override
	public List<String> productionViolations() {
		List<String> problems = new ArrayList<>();
		if (isPublished(properties.fieldKey())) {
			problems.add("eloka.crypto.field-key is a published development/test key");
		}
		if (isPublished(properties.indexKey())) {
			problems.add("eloka.crypto.index-key is a published development/test key");
		}
		return problems;
	}

	/** Compares decoded bytes, so a published key with different padding or whitespace still counts. */
	private static boolean isPublished(String base64Key) {
		byte[] key = decodeOrNull(base64Key);
		return key != null && PUBLISHED_KEYS.stream().anyMatch(published -> Arrays.equals(decodeOrNull(published), key));
	}

	private static byte[] decodeOrNull(String base64) {
		try {
			return base64 == null ? null : Base64.getMimeDecoder().decode(base64.strip());
		}
		catch (IllegalArgumentException ex) {
			return null;
		}
	}
}
