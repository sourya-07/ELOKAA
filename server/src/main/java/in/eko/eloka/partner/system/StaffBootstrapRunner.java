package in.eko.eloka.partner.system;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;
import in.eko.eloka.partner.identity.StaffAdmin;
import in.eko.eloka.partner.identity.StaffView;
import in.eko.eloka.partner.shared.error.DomainException;
import in.eko.eloka.partner.shared.security.Role;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Profile {@code staff-bootstrap}: creates or updates one staff account from
 * {@code ELOKA_STAFF_EMAIL}, {@code ELOKA_STAFF_NAME}, {@code ELOKA_STAFF_ROLES} (REVIEWER,
 * APPROVER, comma-separated) and {@code ELOKA_STAFF_ACTIVE} (default true), then exits. The staff
 * member then signs in with Google using that email (docs/deploy/RUNBOOK.md).
 */
@Component
@Profile("staff-bootstrap")
class StaffBootstrapRunner implements ApplicationRunner {

	private static final Logger log = LoggerFactory.getLogger(StaffBootstrapRunner.class);
	private static final Set<Role> STAFF_ROLES = EnumSet.of(Role.REVIEWER, Role.APPROVER);

	private final StaffAdmin staff;
	private final ConfigurableApplicationContext context;
	private final String email;
	private final String name;
	private final String roles;
	private final boolean active;

	StaffBootstrapRunner(StaffAdmin staff, ConfigurableApplicationContext context,
			@Value("${ELOKA_STAFF_EMAIL:}") String email, @Value("${ELOKA_STAFF_NAME:}") String name,
			@Value("${ELOKA_STAFF_ROLES:}") String roles, @Value("${ELOKA_STAFF_ACTIVE:true}") boolean active) {
		this.staff = staff;
		this.context = context;
		this.email = email;
		this.name = name;
		this.roles = roles;
		this.active = active;
	}

	@Override
	public void run(ApplicationArguments args) {
		int code = 0;
		try {
			Set<Role> parsed = EnumSet.noneOf(Role.class);
			Arrays.stream(roles.split(",")).map(String::trim).filter(role -> !role.isEmpty())
				.map(role -> Role.valueOf(role.toUpperCase()))
				.forEach(parsed::add);
			if (!STAFF_ROLES.containsAll(parsed)) {
				throw new IllegalArgumentException("Staff roles are REVIEWER and APPROVER");
			}
			StaffView saved = staff.save(email, name.isBlank() ? null : name, parsed, active);
			log.info("Staff account saved: {} roles={} active={}", saved.id(), saved.roles(), saved.active());
		}
		catch (DomainException | IllegalArgumentException ex) {
			log.error("Staff bootstrap refused: {}", ex.getMessage());
			code = 1;
		}
		int exitCode = code;
		System.exit(SpringApplication.exit(context, () -> exitCode));
	}
}
