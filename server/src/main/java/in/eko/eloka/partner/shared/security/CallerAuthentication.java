package in.eko.eloka.partner.shared.security;

import java.util.ArrayList;
import java.util.List;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

/** Spring Security wrapper around a {@link Caller}: authorities {@code KIND_*} and {@code ROLE_*}. */
public final class CallerAuthentication extends AbstractAuthenticationToken {

	private final Caller caller;

	public CallerAuthentication(Caller caller) {
		super(authorities(caller));
		this.caller = caller;
		setAuthenticated(true);
	}

	private static List<GrantedAuthority> authorities(Caller caller) {
		List<GrantedAuthority> authorities = new ArrayList<>();
		authorities.add(new SimpleGrantedAuthority("KIND_" + caller.kind().name()));
		caller.roles().forEach(role -> authorities.add(new SimpleGrantedAuthority("ROLE_" + role.name())));
		return authorities;
	}

	@Override
	public Object getCredentials() {
		return "";
	}

	@Override
	public Caller getPrincipal() {
		return caller;
	}
}
