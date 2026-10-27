package in.eko.eloka.partner;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.docs.Documenter;

/**
 * Fails the build when one module reaches into another module's internals or when
 * modules depend on each other in a cycle. Also writes module diagrams to build/spring-modulith-docs.
 */
class ModularityTests {

	private final ApplicationModules modules = ApplicationModules.of(ElokaPartnerApplication.class);

	@Test
	void modulesRespectTheirBoundaries() {
		modules.verify();
	}

	@Test
	void writeModuleDocumentation() {
		new Documenter(modules).writeDocumentation();
	}
}
