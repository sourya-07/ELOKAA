package in.eko.eloka.partner.shared.security;

import java.util.Optional;

/** Turns a bearer token into a {@link Caller}. Implemented by the identity module. */
public interface CallerResolver {

	Optional<Caller> resolve(String bearerToken);
}
