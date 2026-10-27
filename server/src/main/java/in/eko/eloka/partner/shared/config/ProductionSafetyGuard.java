package in.eko.eloka.partner.shared.config;

import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.stereotype.Component;

/**
 * Stops a staging or production start that still carries development shortcuts (ADR-0004).
 * Staging is held to the same rule so it proves the real configuration. Runs after all
 * singletons exist, so every {@link UnsafeInProduction} bean and {@link SafetyCheck} is seen.
 */
@Component
public class ProductionSafetyGuard implements SmartInitializingSingleton {

	private static final Logger log = LoggerFactory.getLogger(ProductionSafetyGuard.class);

	private final ElokaProperties properties;
	private final ObjectProvider<UnsafeInProduction> unsafeBeans;
	private final ObjectProvider<SafetyCheck> checks;

	public ProductionSafetyGuard(ElokaProperties properties, ObjectProvider<UnsafeInProduction> unsafeBeans,
			ObjectProvider<SafetyCheck> checks) {
		this.properties = properties;
		this.unsafeBeans = unsafeBeans;
		this.checks = checks;
	}

	@Override
	public void afterSingletonsInstantiated() {
		List<String> problems = new ArrayList<>();
		unsafeBeans.orderedStream().forEach(bean -> problems.add(bean.unsafeReason()));
		checks.orderedStream().forEach(check -> problems.addAll(check.productionViolations()));

		if (problems.isEmpty()) {
			return;
		}
		if (!properties.environment().allowsDevelopmentShortcuts()) {
			throw new IllegalStateException("Refusing to start in " + properties.environment() + ". Fix: "
					+ String.join("; ", problems));
		}
		log.warn("Development-only behaviour is active (allowed in {}): {}", properties.environment(), problems);
	}
}
