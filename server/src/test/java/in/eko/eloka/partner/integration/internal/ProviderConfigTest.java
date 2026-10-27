package in.eko.eloka.partner.integration.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import in.eko.eloka.partner.integration.MobileOtpGateway;
import in.eko.eloka.partner.integration.ProviderNotConfiguredException;
import in.eko.eloka.partner.shared.config.UnsafeInProduction;
import org.junit.jupiter.api.Test;

class ProviderConfigTest {

	private final ProviderConfig config = new ProviderConfig();

	@Test
	void noProviderRefusesInsteadOfPretending() {
		MobileOtpGateway gateway = config.mobileOtpGateway(new IntegrationProperties(null, null, null), Clock.systemUTC());
		assertThatThrownBy(() -> gateway.send("9876543210")).isInstanceOf(ProviderNotConfiguredException.class);
		assertThatThrownBy(() -> gateway.verify("9876543210", "r", "123456"))
			.isInstanceOf(ProviderNotConfiguredException.class);
	}

	@Test
	void theFakeIsMarkedUnsafeSoStagingAndProductionRefuseIt() {
		MobileOtpGateway gateway = config.mobileOtpGateway(
				new IntegrationProperties(IntegrationProperties.Provider.FAKE, "654321", null), Clock.systemUTC());
		assertThat(gateway).isInstanceOf(UnsafeInProduction.class);
		assertThat(gateway.verify("9876543210", gateway.send("9876543210").reference(), "654321")).isTrue();
		assertThat(gateway.verify("9876543210", "r", "123456")).isFalse();
	}

	@Test
	void epsNeedsItsSettings() {
		assertThatThrownBy(() -> config.mobileOtpGateway(
				new IntegrationProperties(IntegrationProperties.Provider.EPS, null, null), Clock.systemUTC()))
			.isInstanceOf(IllegalStateException.class);
	}
}
