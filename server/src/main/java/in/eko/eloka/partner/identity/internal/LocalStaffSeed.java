package in.eko.eloka.partner.identity.internal;

import java.util.Set;
import in.eko.eloka.partner.shared.config.UnsafeInProduction;
import in.eko.eloka.partner.shared.security.Role;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Local and test only: three sample staff accounts that sign in with OTP 123456
 * (docs/LOCAL_DEVELOPMENT.md). Staging and production refuse to start with this bean.
 */
@Component
@Profile({ "local", "test" })
class LocalStaffSeed implements ApplicationRunner, UnsafeInProduction {

	static final String REVIEWER_MOBILE = "9000000006";
	static final String APPROVER_MOBILE = "9000000007";
	static final String BOTH_MOBILE = "9000000008";

	private final StaffService staff;

	LocalStaffSeed(StaffService staff) {
		this.staff = staff;
	}

	@Override
	public void run(ApplicationArguments args) {
		staff.upsert("reviewer@example.com", "Sample Reviewer", REVIEWER_MOBILE, Set.of(Role.REVIEWER), true);
		staff.upsert("approver@example.com", "Sample Approver", APPROVER_MOBILE, Set.of(Role.APPROVER), true);
		staff.upsert("lead@example.com", "Sample Lead", BOTH_MOBILE, Set.of(Role.REVIEWER, Role.APPROVER), true);
	}

	@Override
	public String unsafeReason() {
		return "sample staff accounts are seeded";
	}
}
