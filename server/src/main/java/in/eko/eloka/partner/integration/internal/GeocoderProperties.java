package in.eko.eloka.partner.integration.internal;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * The address lookup for the live photo's location, under {@code eloka.integration.geocoder}
 * (docs/OPEN_QUESTIONS.md C-11). Using it sends the applicant's coordinates to that service.
 *
 * @param provider {@code none} (no address), {@code fake} (local/test only) or {@code nominatim} (OpenStreetMap)
 * @param baseUrl Nominatim's address, HTTPS only (default the free public service)
 * @param userAgent how this app identifies itself (the public service's usage policy requires one)
 * @param email a contact address sent with each lookup (asked for by the public service; optional)
 * @param timeout connection and answer limit for one lookup
 */
@ConfigurationProperties("eloka.integration.geocoder")
record GeocoderProperties(Provider provider, String baseUrl, String userAgent, String email, Duration timeout) {

	enum Provider {
		NONE,
		FAKE,
		NOMINATIM
	}

	GeocoderProperties {
		provider = provider == null ? Provider.NONE : provider;
		baseUrl = baseUrl == null || baseUrl.isBlank() ? "https://nominatim.openstreetmap.org" : baseUrl;
		userAgent = userAgent == null || userAgent.isBlank() ? "ELOKA-partner-onboarding/1.0" : userAgent;
		timeout = timeout == null ? Duration.ofSeconds(4) : timeout;
	}
}
