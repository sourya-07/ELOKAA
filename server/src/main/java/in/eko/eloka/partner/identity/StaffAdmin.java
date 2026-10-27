package in.eko.eloka.partner.identity;

import java.util.Set;
import in.eko.eloka.partner.shared.security.Role;

/** Creating and changing staff accounts (the bootstrap command; there is no admin screen yet). */
public interface StaffAdmin {

	/**
	 * Creates or updates the account for this Google email. A blank name or no roles keep what an
	 * existing account has; {@code active=false} disables it.
	 */
	StaffView save(String email, String displayName, Set<Role> roles, boolean active);
}
