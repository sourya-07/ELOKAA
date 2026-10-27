package in.eko.eloka.partner.integration.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import in.eko.eloka.partner.integration.MobileOtpGateway;
import in.eko.eloka.partner.integration.ProviderUnavailableException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/** The EPS adapter against a mock HTTP server. Not yet run against EPS UAT (no credentials). */
class EpsMobileOtpGatewayTest {

	private static final String BASE = "https://staging.eko.in/ekoapi/v3";
	private static final IntegrationProperties.Eps CONFIG = new IntegrationProperties.Eps(BASE, "dev-key",
			"access-key", "9000000000", "9000000001", null, null);
	private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-27T10:00:00Z"), ZoneOffset.UTC);

	private MockRestServiceServer server;
	private EpsMobileOtpGateway gateway;

	@BeforeEach
	void setUp() {
		RestClient.Builder builder = RestClient.builder().baseUrl(BASE);
		server = MockRestServiceServer.bindTo(builder).build();
		gateway = new EpsMobileOtpGateway(new EpsClient(builder.build(), CONFIG, CLOCK), CONFIG.cspId());
	}

	@Test
	void sendSignsTheRequestAndReadsTheReference() {
		EpsClient signer = new EpsClient(RestClient.create(), CONFIG, CLOCK);
		server.expect(requestTo(BASE + "/tools/kyc/mobile/otp"))
			.andExpect(method(HttpMethod.POST))
			.andExpect(header("developer_key", "dev-key"))
			.andExpect(header("secret-key-timestamp", "1793095200000"))
			.andExpect(header("secret-key", signer.sign("1793095200000")))
			.andExpect(jsonPath("$.mobile").value("9876543210"))
			.andExpect(jsonPath("$.csp_id").value("9000000001"))
			.andExpect(jsonPath("$.initiator_id").value("9000000000"))
			.andRespond(withSuccess("""
					{"status":0,"message":"OTP has been sent","data":{"tid":"2886978474",
					"otp_expiry_timestamp":"Tue Oct 27 15:35:00 IST 2026"}}""", MediaType.APPLICATION_JSON));

		MobileOtpGateway.SentOtp sent = gateway.send("9876543210");

		assertThat(sent.reference()).isEqualTo("2886978474");
		assertThat(sent.expiresAt()).isEqualTo(Instant.parse("2026-10-27T10:05:00Z"));
		server.verify();
	}

	@Test
	void signatureIsBase64HmacOfTheTimestampWithTheBase64Key() {
		// Independently computed: base64(HMAC-SHA256(key = base64("access-key"), "1793095200000"))
		assertThat(new EpsClient(RestClient.create(), CONFIG, CLOCK).sign("1793095200000"))
			.isEqualTo(expectedSignature("access-key", "1793095200000"));
	}

	@Test
	void verifyAcceptsOnlyASuccessForTheSameMobile() {
		server.expect(requestTo(BASE + "/tools/kyc/mobile/otp/verify"))
			.andExpect(method(HttpMethod.PUT))
			.andExpect(jsonPath("$.otp_ref_id").value("ref-1"))
			.andRespond(withSuccess("{\"status\":0,\"data\":{\"mobile\":\"9876543210\"}}", MediaType.APPLICATION_JSON));
		server.expect(requestTo(BASE + "/tools/kyc/mobile/otp/verify"))
			.andRespond(withSuccess("{\"status\":0,\"data\":{\"mobile\":\"9999999999\"}}", MediaType.APPLICATION_JSON));
		server.expect(requestTo(BASE + "/tools/kyc/mobile/otp/verify"))
			.andRespond(withSuccess("{\"status\":1,\"message\":\"OTP verification failed.\",\"data\":{}}",
					MediaType.APPLICATION_JSON));

		assertThat(gateway.verify("9876543210", "ref-1", "123456")).isTrue();
		assertThat(gateway.verify("9876543210", "ref-1", "123456")).isFalse();
		assertThat(gateway.verify("9876543210", "ref-1", "000000")).isFalse();
	}

	@Test
	void serverErrorsAndBodiesWithoutStatusAreUnavailableNotSuccess() {
		server.expect(requestTo(BASE + "/tools/kyc/mobile/otp")).andRespond(withServerError());
		server.expect(requestTo(BASE + "/tools/kyc/mobile/otp/verify"))
			.andRespond(withSuccess("{\"data\":{}}", MediaType.APPLICATION_JSON));

		assertThatThrownBy(() -> gateway.send("9876543210")).isInstanceOf(ProviderUnavailableException.class);
		assertThatThrownBy(() -> gateway.verify("9876543210", "r", "123456"))
			.isInstanceOf(ProviderUnavailableException.class);
	}

	@Test
	void onlyEpsHostsOverHttpsCanBeConfigured() {
		for (String url : new String[] { "http://staging.eko.in/ekoapi/v3", "https://evil.example/ekoapi/v3",
				"https://staging.eko.in.evil.example/v3" }) {
			assertThatThrownBy(() -> EpsClient.validate(new IntegrationProperties.Eps(url, "k", "a", "i", null, null, null)))
				.isInstanceOf(IllegalStateException.class);
		}
	}

	private static String expectedSignature(String accessKey, String timestamp) {
		try {
			javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
			mac.init(new javax.crypto.spec.SecretKeySpec(
					java.util.Base64.getEncoder().encode(accessKey.getBytes(java.nio.charset.StandardCharsets.UTF_8)),
					"HmacSHA256"));
			return java.util.Base64.getEncoder()
				.encodeToString(mac.doFinal(timestamp.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
		}
		catch (Exception ex) {
			throw new IllegalStateException(ex);
		}
	}
}
