package in.eko.eloka.partner.identity;

import java.util.Set;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.UUID;
import in.eko.eloka.partner.shared.security.Role;

/** A staff member as other modules see them: never the full email. */
public record StaffView(UUID id, @JsonProperty("email_masked") String emailMasked,
		@JsonProperty("display_name") String displayName, Set<Role> roles, boolean active) {
}
