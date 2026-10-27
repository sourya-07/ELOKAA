package in.eko.eloka.partner.integration.internal;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import in.eko.eloka.partner.integration.CreditBureau;
import in.eko.eloka.partner.integration.DigiLockerProvider;
import in.eko.eloka.partner.integration.MalwareScanner;
import in.eko.eloka.partner.integration.MobileOtpGateway;
import in.eko.eloka.partner.integration.PanVerifier;

/**
 * Picks one implementation per port from {@link IntegrationProperties}. Fakes are allowed by
 * this class in any profile, but they implement {@code UnsafeInProduction}, so
 * {@code ProductionSafetyGuard} stops staging and production from starting with one.
 */
@Configuration
class ProviderConfig {

	@Bean
	MobileOtpGateway mobileOtpGateway(IntegrationProperties properties, Clock clock) {
		return switch (properties.otpProvider()) {
			case FAKE -> new FakeMobileOtpGateway(properties.fakeOtp());
			case EPS -> new EpsMobileOtpGateway(EpsClient.create(properties.eps(), clock),
					properties.eps().cspId());
			case NONE -> new UnconfiguredMobileOtpGateway();
		};
	}

	@Bean
	PanVerifier panVerifier(IntegrationProperties properties, Clock clock) {
		return switch (properties.identityProvider()) {
			case FAKE -> new FakeIdentityProviders.Pan();
			case EPS -> new EpsPanVerifier(EpsClient.create(properties.eps(), clock));
			case NONE -> new UnconfiguredIdentityProviders.Pan();
		};
	}

	@Bean
	DigiLockerProvider digiLockerProvider(IntegrationProperties properties, Clock clock) {
		return switch (properties.identityProvider()) {
			case FAKE -> new FakeIdentityProviders.DigiLocker();
			case EPS -> new EpsDigiLocker(EpsClient.create(properties.eps(), clock));
			case NONE -> new UnconfiguredIdentityProviders.DigiLocker();
		};
	}

	/** No real bureau yet (docs/OPEN_QUESTIONS.md Q-1): {@code eps} is not a credit bureau and is refused. */
	@Bean
	CreditBureau creditBureau(IntegrationProperties properties) {
		return switch (properties.creditBureau()) {
			case FAKE -> new FakeIdentityProviders.Bureau();
			case EPS -> throw new IllegalStateException("eloka.integration.credit-bureau: EPS has no credit bureau API");
			case NONE -> new UnconfiguredIdentityProviders.Bureau();
		};
	}

	@Bean
	MalwareScanner malwareScanner(IntegrationProperties properties) {
		return switch (properties.malwareScanner()) {
			case FAKE -> new FakeMalwareScanner();
			case CLAMAV -> new ClamAvScanner(properties.clamav().host(), properties.clamav().port(),
					properties.clamav().timeout());
			case NONE -> new UnconfiguredMalwareScanner();
		};
	}
}
