package in.eko.eloka.partner.shared.security;

import java.util.Set;
import java.util.UUID;

/**
 * The authenticated party behind a request. Built only from a verified session, so every
 * field can be trusted. Controllers must use this instead of identity fields in request bodies.
 *
 * @param sessionId session row id (safe to log as a short reference)
 * @param kind what the session is for
 * @param subjectId applicant id or staff user id
 * @param roles roles of the caller
 */
public record Caller(UUID sessionId, SessionKind kind, UUID subjectId, Set<Role> roles) {

	public Caller {
		roles = roles == null ? Set.of() : Set.copyOf(roles);
	}

	public boolean hasRole(Role role) {
		return roles.contains(role);
	}

	/** Short, non-secret reference for logs. */
	public String sessionRef() {
		return sessionId.toString().substring(0, 8);
	}
}
