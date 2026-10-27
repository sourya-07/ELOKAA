package in.eko.eloka.partner.shared.security;

import java.util.Optional;
import in.eko.eloka.partner.shared.error.UnauthenticatedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/** Access to the caller of the current request. */
public final class CallerContext {

	private CallerContext() {
	}

	public static Optional<Caller> current() {
		Authentication auth = SecurityContextHolder.getContext().getAuthentication();
		if (auth != null && auth.getPrincipal() instanceof Caller caller) {
			return Optional.of(caller);
		}
		return Optional.empty();
	}

	public static Caller require() {
		return current().orElseThrow(() -> new UnauthenticatedException("Please sign in again"));
	}
}
