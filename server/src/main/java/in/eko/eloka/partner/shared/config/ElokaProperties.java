package in.eko.eloka.partner.shared.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * App-wide settings under {@code eloka.*}.
 *
 * @param environment where the app runs ({@code ELOKA_ENVIRONMENT}); production by default in the prod profile
 * @param corsAllowedOriginPatterns browser origins allowed to call the API directly, e.g. {@code https://partner.eloka.in}
 */
@ConfigurationProperties("eloka")
public record ElokaProperties(ElokaEnvironment environment, List<String> corsAllowedOriginPatterns) {

	public ElokaProperties {
		environment = environment == null ? ElokaEnvironment.LOCAL : environment;
		corsAllowedOriginPatterns = corsAllowedOriginPatterns == null ? List.of() : List.copyOf(corsAllowedOriginPatterns);
	}
}
