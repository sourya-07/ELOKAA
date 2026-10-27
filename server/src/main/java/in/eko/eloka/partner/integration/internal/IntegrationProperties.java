package in.eko.eloka.partner.integration.internal;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Which provider each port uses, under {@code eloka.integration}. The default for every port is
 * {@code none}: the "not configured" stub.
 *
 * @param otpProvider {@code fake} (local/test only), {@code eps} or {@code none}
 * @param fakeOtp the code the fake OTP provider accepts
 * @param eps EPS connection
 */
@ConfigurationProperties("eloka.integration")
record IntegrationProperties(Provider otpProvider, String fakeOtp, Eps eps) {

	enum Provider {
		NONE,
		FAKE,
		EPS
	}

	IntegrationProperties {
		otpProvider = otpProvider == null ? Provider.NONE : otpProvider;
		fakeOtp = fakeOtp == null || fakeOtp.isBlank() ? "123456" : fakeOtp;
		eps = eps == null ? new Eps(null, null, null, null, null, null, null) : eps;
	}

	/**
	 * @param baseUrl e.g. {@code https://staging.eko.in/ekoapi/v3} (UAT) or {@code https://api.eko.in/ekoicici/v3}
	 * @param developerKey static API key ({@code developer_key} header)
	 * @param accessKey secret used to sign every request ({@code secret-key}); never logged
	 * @param initiatorId registered mobile of the API user
	 * @param cspId customer service point id sent with mobile OTP requests
	 * @param connectTimeout connection timeout
	 * @param readTimeout response timeout
	 */
	record Eps(String baseUrl, String developerKey, String accessKey, String initiatorId, String cspId,
			Duration connectTimeout, Duration readTimeout) {

		Eps {
			connectTimeout = connectTimeout == null ? Duration.ofSeconds(5) : connectTimeout;
			readTimeout = readTimeout == null ? Duration.ofSeconds(15) : readTimeout;
		}
	}
}
