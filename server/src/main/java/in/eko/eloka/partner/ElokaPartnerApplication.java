package in.eko.eloka.partner;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;

/**
 * ELOKA partner onboarding backend. Modules are the sub-packages of this package.
 * Spring's default in-memory user is excluded: authentication is bearer tokens only, and the
 * default would print a generated password into the logs.
 */
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
@ConfigurationPropertiesScan
public class ElokaPartnerApplication {

	public static void main(String[] args) {
		SpringApplication.run(ElokaPartnerApplication.class, args);
	}

}
