package in.eko.eloka.partner.integration.internal;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Which provider each port uses, under {@code eloka.integration}. The default for every port is
 * {@code none}: the "not configured" stub.
 *
 * @param otpProvider mobile OTP: {@code fake} (local/test only), {@code eps} or {@code none}
 * @param fakeOtp the code the fake OTP provider accepts
 * @param identityProvider PAN and DigiLocker checks: {@code fake}, {@code eps} or {@code none}
 * @param creditBureau CIBIL score: {@code fake} or {@code none} (no real provider yet, docs/OPEN_QUESTIONS.md Q-1)
 * @param malwareScanner {@code fake}, {@code clamav} or {@code none}
 * @param eps EPS connection
 * @param clamav clamd connection
 */
@ConfigurationProperties("eloka.integration")
record IntegrationProperties(Provider otpProvider, String fakeOtp, Provider identityProvider, Provider creditBureau,
		Scanner malwareScanner, Eps eps, ClamAv clamav) {

	enum Provider {
		NONE,
		FAKE,
		EPS
	}

	enum Scanner {
		NONE,
		FAKE,
		CLAMAV
	}

	IntegrationProperties {
		otpProvider = otpProvider == null ? Provider.NONE : otpProvider;
		fakeOtp = fakeOtp == null || fakeOtp.isBlank() ? "123456" : fakeOtp;
		identityProvider = identityProvider == null ? Provider.NONE : identityProvider;
		creditBureau = creditBureau == null ? Provider.NONE : creditBureau;
		malwareScanner = malwareScanner == null ? Scanner.NONE : malwareScanner;
		eps = eps == null ? new Eps(null, null, null, null, null, null, null) : eps;
		clamav = clamav == null ? new ClamAv(null, 0, null) : clamav;
	}

	/** @param host clamd host; @param port clamd port (3310); @param timeout per scan */
	record ClamAv(String host, int port, Duration timeout) {

		ClamAv {
			port = port == 0 ? 3310 : port;
			timeout = timeout == null ? Duration.ofSeconds(30) : timeout;
		}
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
