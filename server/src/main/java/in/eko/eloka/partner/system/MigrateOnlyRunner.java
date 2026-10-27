package in.eko.eloka.partner.system;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Profile {@code migrate}: the app starts (Flyway runs, Hibernate validates the schema) and then
 * exits with code 0. The one-shot migration job in deploy/docker-compose.yml runs it before the
 * server starts; the server itself never changes the schema. Flyway's own lock table keeps two
 * jobs from migrating at once.
 */
@Component
@Profile("migrate")
class MigrateOnlyRunner implements ApplicationRunner {

	private static final Logger log = LoggerFactory.getLogger(MigrateOnlyRunner.class);

	private final ConfigurableApplicationContext context;

	MigrateOnlyRunner(ConfigurableApplicationContext context) {
		this.context = context;
	}

	@Override
	public void run(ApplicationArguments args) {
		log.info("Database migrated and schema validated; exiting (migrate profile).");
		System.exit(SpringApplication.exit(context, () -> 0));
	}
}
