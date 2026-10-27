package in.eko.eloka.partner.shared.config;

import java.util.List;

/**
 * A component that can tell whether its current configuration is safe for production
 * (for example: not using the published development keys).
 */
public interface SafetyCheck {

	/** @return human-readable problems; empty when safe */
	List<String> productionViolations();
}
