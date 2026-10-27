package in.eko.eloka.partner.shared;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import in.eko.eloka.partner.shared.config.ElokaEnvironment;
import in.eko.eloka.partner.shared.config.ElokaProperties;
import in.eko.eloka.partner.shared.config.ProductionSafetyGuard;
import in.eko.eloka.partner.shared.config.SafetyCheck;
import in.eko.eloka.partner.shared.config.UnsafeInProduction;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class ProductionSafetyGuardTest {

	private final ApplicationContextRunner runner = new ApplicationContextRunner()
		.withBean(ProductionSafetyGuard.class);

	@Test
	void productionRefusesDevelopmentBeans() {
		runner.withBean(ElokaProperties.class, () -> new ElokaProperties(ElokaEnvironment.PRODUCTION, List.of()))
			.withBean("fixedOtp", UnsafeInProduction.class, () -> () -> "fixed OTP")
			.run(context -> assertThat(context).hasFailed()
				.getFailure()
				.hasMessageContaining("Refusing to start in PRODUCTION")
				.hasMessageContaining("fixed OTP"));
	}

	@Test
	void productionRefusesFailedSafetyChecks() {
		runner.withBean(ElokaProperties.class, () -> new ElokaProperties(ElokaEnvironment.PRODUCTION, List.of()))
			.withBean("devKey", SafetyCheck.class, () -> () -> List.of("development key in use"))
			.run(context -> assertThat(context).hasFailed());
	}

	@Test
	void productionStartsWhenClean() {
		runner.withBean(ElokaProperties.class, () -> new ElokaProperties(ElokaEnvironment.PRODUCTION, List.of()))
			.withBean("clean", SafetyCheck.class, () -> List::of)
			.run(context -> assertThat(context).hasNotFailed());
	}

	@Test
	void stagingIsHeldToTheSameRules() {
		runner.withBean(ElokaProperties.class, () -> new ElokaProperties(ElokaEnvironment.STAGING, List.of()))
			.withBean("fixedOtp", UnsafeInProduction.class, () -> () -> "fixed OTP")
			.run(context -> assertThat(context).hasFailed()
				.getFailure()
				.hasMessageContaining("Refusing to start in STAGING"));
	}

	@Test
	void localOnlyWarns() {
		runner.withBean(ElokaProperties.class, () -> new ElokaProperties(ElokaEnvironment.LOCAL, List.of()))
			.withBean("fixedOtp", UnsafeInProduction.class, () -> () -> "fixed OTP")
			.run(context -> assertThat(context).hasNotFailed());
	}
}
