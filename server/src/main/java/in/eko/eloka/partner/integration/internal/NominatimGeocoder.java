package in.eko.eloka.partner.integration.internal;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import in.eko.eloka.partner.integration.ReverseGeocoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * OpenStreetMap's Nominatim, the free public service (owner's choice, docs/OPEN_QUESTIONS.md C-11). Its
 * usage policy allows at most one request per second and needs an identifying User-Agent, so lookups
 * here wait their turn. Any failure is logged without the coordinates and answers empty.
 */
class NominatimGeocoder implements ReverseGeocoder {

	private static final Logger log = LoggerFactory.getLogger(NominatimGeocoder.class);
	private static final long MIN_GAP_MS = 1_000;

	private final RestClient http;
	private final String email;
	private long lastCall;

	NominatimGeocoder(GeocoderProperties properties) {
		this(properties, RestClient.builder().requestFactory(timeouts(properties)));
	}

	/** With a prepared client builder (tests bind a mock server to it). */
	NominatimGeocoder(GeocoderProperties properties, RestClient.Builder builder) {
		if (!properties.baseUrl().startsWith("https://")) {
			throw new IllegalStateException("eloka.integration.geocoder.base-url must be HTTPS");
		}
		this.http = builder.baseUrl(properties.baseUrl())
			.defaultHeader("User-Agent", properties.userAgent())
			.defaultHeader("Accept-Language", "en")
			.build();
		this.email = properties.email();
	}

	private static SimpleClientHttpRequestFactory timeouts(GeocoderProperties properties) {
		SimpleClientHttpRequestFactory timeouts = new SimpleClientHttpRequestFactory();
		timeouts.setConnectTimeout(properties.timeout());
		timeouts.setReadTimeout(properties.timeout());
		return timeouts;
	}

	@Override
	public Optional<String> addressOf(double latitude, double longitude) {
		waitForTurn();
		try {
			Map<?, ?> answer = http.get()
				.uri(builder -> {
					builder.path("/reverse")
						.queryParam("format", "jsonv2")
						.queryParam("lat", String.format(Locale.ROOT, "%.6f", latitude))
						.queryParam("lon", String.format(Locale.ROOT, "%.6f", longitude))
						.queryParam("zoom", 18)
						.queryParam("addressdetails", 0);
					if (email != null && !email.isBlank()) {
						builder.queryParam("email", email);
					}
					return builder.build();
				})
				.retrieve()
				.body(Map.class);
			Object name = answer == null ? null : answer.get("display_name");
			return name instanceof String text && !text.isBlank() ? Optional.of(text.strip()) : Optional.empty();
		}
		catch (RestClientException | IllegalArgumentException ex) {
			log.warn("Address lookup failed: {}", ex.getClass().getSimpleName());
			return Optional.empty();
		}
	}

	/** One request per second at most, across the app (the public service's rule). */
	private synchronized void waitForTurn() {
		long wait = lastCall + MIN_GAP_MS - System.currentTimeMillis();
		if (wait > 0) {
			try {
				Thread.sleep(wait);
			}
			catch (InterruptedException ex) {
				Thread.currentThread().interrupt();
			}
		}
		lastCall = System.currentTimeMillis();
	}
}
