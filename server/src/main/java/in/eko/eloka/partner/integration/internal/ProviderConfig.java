package in.eko.eloka.partner.integration.internal;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import in.eko.eloka.partner.integration.MobileOtpGateway;

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
}
