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
		MobileOtpGateway gateway = config.mobileOtpGateway(new IntegrationProperties(null, null, null, null, null, null, null), Clock.systemUTC());
		assertThatThrownBy(() -> gateway.send("9876543210")).isInstanceOf(ProviderNotConfiguredException.class);
		assertThatThrownBy(() -> gateway.verify("9876543210", "r", "123456"))
			.isInstanceOf(ProviderNotConfiguredException.class);
	}

	@Test
	void theFakeIsMarkedUnsafeSoStagingAndProductionRefuseIt() {
		MobileOtpGateway gateway = config.mobileOtpGateway(
				new IntegrationProperties(IntegrationProperties.Provider.FAKE, "654321", null, null, null, null, null), Clock.systemUTC());
		assertThat(gateway).isInstanceOf(UnsafeInProduction.class);
		assertThat(gateway.verify("9876543210", gateway.send("9876543210").reference(), "654321")).isTrue();
		assertThat(gateway.verify("9876543210", "r", "123456")).isFalse();
	}

	@Test
	void epsNeedsItsSettings() {
		assertThatThrownBy(() -> config.mobileOtpGateway(
				new IntegrationProperties(IntegrationProperties.Provider.EPS, null, null, null, null, null, null), Clock.systemUTC()))
			.isInstanceOf(IllegalStateException.class);
	}

	@Test
	void identityBureauAndScannerRefuseWhenNotConfigured() {
		IntegrationProperties none = new IntegrationProperties(null, null, null, null, null, null, null);
		assertThatThrownBy(() -> config.panVerifier(none, Clock.systemUTC()).verify("ABCPE1234F", "A B", java.time.LocalDate.of(1990, 1, 1)))
			.isInstanceOf(ProviderNotConfiguredException.class);
		assertThatThrownBy(() -> config.digiLockerProvider(none, Clock.systemUTC()).start("DL1", "https://x"))
			.isInstanceOf(ProviderNotConfiguredException.class);
		assertThatThrownBy(() -> config.creditBureau(none).fetchScore(null)).isInstanceOf(ProviderNotConfiguredException.class);
		assertThatThrownBy(() -> config.malwareScanner(none).scan(new byte[] { 1 }))
			.isInstanceOf(ProviderNotConfiguredException.class);
	}

	@Test
	void epsIsNotACreditBureau() {
		assertThatThrownBy(() -> config.creditBureau(new IntegrationProperties(null, null, null,
				IntegrationProperties.Provider.EPS, null, null, null))).isInstanceOf(IllegalStateException.class);
	}

	@Test
	void fakeScannerFlagsTheEicarString() {
		var scanner = config.malwareScanner(new IntegrationProperties(null, null, null, null,
				IntegrationProperties.Scanner.FAKE, null, null));
		assertThat(scanner.scan(("%PDF-1.4 " + FakeMalwareScanner.EICAR).getBytes()))
			.isEqualTo(in.eko.eloka.partner.integration.MalwareScanner.Verdict.INFECTED);
		assertThat(scanner.scan("%PDF-1.4 clean".getBytes())).isEqualTo(in.eko.eloka.partner.integration.MalwareScanner.Verdict.CLEAN);
	}
}
