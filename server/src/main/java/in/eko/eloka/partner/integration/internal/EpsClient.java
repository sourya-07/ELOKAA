package in.eko.eloka.partner.integration.internal;

import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.Clock;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import in.eko.eloka.partner.integration.ProviderUnavailableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.databind.JsonNode;

/**
 * Signed calls to EPS (Eko Platform Services). Each request carries {@code developer_key},
 * {@code secret-key-timestamp} and {@code secret-key = base64(HMAC-SHA256(key = base64(access_key),
 * timestamp))}. Only the allow-listed EPS hosts can be configured, so no request can be sent
 * anywhere else. Bodies are never logged: they hold mobiles and identity numbers.
 */
class EpsClient {

	static final Set<String> ALLOWED_HOSTS = Set.of("staging.eko.in", "api.eko.in");

	private static final Logger log = LoggerFactory.getLogger(EpsClient.class);

	private final RestClient rest;
	private final IntegrationProperties.Eps config;
	private final Clock clock;

	EpsClient(RestClient rest, IntegrationProperties.Eps config, Clock clock) {
		validate(config);
		this.rest = rest;
		this.config = config;
		this.clock = clock;
	}

	/** A client with the configured timeouts. */
	static EpsClient create(IntegrationProperties.Eps config, Clock clock) {
		validate(config);
		JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(
				HttpClient.newBuilder().connectTimeout(config.connectTimeout()).build());
		factory.setReadTimeout(config.readTimeout());
		return new EpsClient(RestClient.builder().baseUrl(config.baseUrl()).requestFactory(factory).build(), config,
				clock);
	}

	static void validate(IntegrationProperties.Eps config) {
		URI base = URI.create(require(config.baseUrl(), "base-url"));
		if (!"https".equals(base.getScheme()) || !ALLOWED_HOSTS.contains(base.getHost())) {
			throw new IllegalStateException("eloka.integration.eps.base-url must be https on " + ALLOWED_HOSTS);
		}
		require(config.developerKey(), "developer-key");
		require(config.accessKey(), "access-key");
		require(config.initiatorId(), "initiator-id");
	}

	String initiatorId() {
		return config.initiatorId();
	}

	/**
	 * @param operation short name for logs, e.g. {@code mobile-otp-send}
	 * @return the parsed response; callers read {@code status} (0 = success) and {@code data}
	 * @throws ProviderUnavailableException on timeouts, HTTP errors or a body without {@code status}
	 */
	JsonNode call(String operation, HttpMethod method, String path, Map<String, Object> body) {
		long started = System.nanoTime();
		Map<String, Object> payload = new LinkedHashMap<>(body);
		payload.putIfAbsent("initiator_id", config.initiatorId());
		String timestamp = Long.toString(clock.millis());
		try {
			JsonNode response = rest.method(method)
				.uri(path)
				.contentType(MediaType.APPLICATION_JSON)
				.accept(MediaType.APPLICATION_JSON)
				.header("developer_key", config.developerKey())
				.header("secret-key-timestamp", timestamp)
				.header("secret-key", sign(timestamp))
				.body(payload)
				.retrieve()
				.body(JsonNode.class);
			if (response == null || !response.path("status").isIntegralNumber()) {
				log.warn("EPS {} answered without a numeric status", operation);
				throw new ProviderUnavailableException("EPS");
			}
			log.info("EPS {} status={} type={} ms={}", operation, response.get("status").asInt(),
					response.path("response_type_id").asInt(-1), (System.nanoTime() - started) / 1_000_000);
			return response;
		}
		catch (RestClientException ex) {
			log.warn("EPS {} failed after {} ms: {}", operation, (System.nanoTime() - started) / 1_000_000,
					ex.getClass().getSimpleName());
			throw new ProviderUnavailableException("EPS");
		}
	}

	String sign(String timestamp) {
		try {
			String encodedKey = Base64.getEncoder().encodeToString(config.accessKey().getBytes(StandardCharsets.UTF_8));
			Mac mac = Mac.getInstance("HmacSHA256");
			mac.init(new SecretKeySpec(encodedKey.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
			return Base64.getEncoder().encodeToString(mac.doFinal(timestamp.getBytes(StandardCharsets.UTF_8)));
		}
		catch (GeneralSecurityException ex) {
			throw new IllegalStateException("EPS signing failed", ex);
		}
	}

	private static String require(String value, String property) {
		if (value == null || value.isBlank()) {
			throw new IllegalStateException("eloka.integration.eps." + property + " is not set");
		}
		return value;
	}
}
