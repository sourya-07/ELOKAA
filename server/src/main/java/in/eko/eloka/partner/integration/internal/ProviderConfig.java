package in.eko.eloka.partner.integration.internal;

import java.time.Clock;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import in.eko.eloka.partner.integration.CreditBureau;
import in.eko.eloka.partner.integration.DigiLockerProvider;
import in.eko.eloka.partner.integration.EmailSender;
import in.eko.eloka.partner.integration.GoogleIdentityVerifier;
import in.eko.eloka.partner.integration.MalwareScanner;
import in.eko.eloka.partner.integration.MobileOtpGateway;
import in.eko.eloka.partner.integration.PanVerifier;
import org.springframework.mail.javamail.JavaMailSender;

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

	/** Staff sign-in; refuses every token while {@code eloka.staff.google-client-id} is empty. */
	@Bean
	GoogleIdentityVerifier googleIdentityVerifier(@Value("${eloka.staff.google-client-id:}") String clientId) {
		return new GoogleIdTokenVerifier(clientId);
	}

	/** Email codes. EPS has no email API, so production needs an SMTP account (docs/OPEN_QUESTIONS.md Q-21). */
	@Bean
	EmailSender emailSender(IntegrationProperties properties, ObjectProvider<JavaMailSender> mail,
			@Value("${spring.mail.host:}") String smtpHost) {
		return switch (properties.emailProvider()) {
			case FAKE -> new FakeEmailSender();
			case SMTP -> {
				// the prod settings always define spring.mail.host (maybe empty), so check the value, not the bean
				if (smtpHost.isBlank() || mail.getIfAvailable() == null) {
					throw new IllegalStateException("eloka.integration.email-provider is smtp but spring.mail.host is not set");
				}
				yield new SmtpEmailSender(mail.getObject(), properties.emailFrom());
			}
			case NONE -> new UnconfiguredEmailSender();
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
