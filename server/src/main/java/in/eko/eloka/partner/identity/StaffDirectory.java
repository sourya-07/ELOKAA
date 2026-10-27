package in.eko.eloka.partner.identity;

import java.util.Optional;
import java.util.UUID;

/** Read access to staff accounts for other modules (names in the case view and audit trail). */
public interface StaffDirectory {

	Optional<StaffView> find(UUID staffId);
}
