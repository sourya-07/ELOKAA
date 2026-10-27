package in.eko.eloka.partner.identity.internal;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import in.eko.eloka.partner.identity.IdentityProperties;
import in.eko.eloka.partner.shared.crypto.Tokens;
import in.eko.eloka.partner.shared.security.Caller;
import in.eko.eloka.partner.shared.security.CallerResolver;
import in.eko.eloka.partner.shared.security.Role;
import in.eko.eloka.partner.shared.security.SessionKind;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Creates, resolves, rotates and revokes sessions. Implements bearer-token authentication. */
@Service
class SessionService implements CallerResolver {

	static final String ACCESS_PREFIX = "ptn_at_";
	static final String REFRESH_PREFIX = "ptn_rt_";

	private final AuthSessionRepository sessions;
	private final StaffUserRepository staff;
	private final IdentityProperties properties;
	private final Clock clock;

	SessionService(AuthSessionRepository sessions, StaffUserRepository staff, IdentityProperties properties,
			Clock clock) {
		this.sessions = sessions;
		this.staff = staff;
		this.properties = properties;
		this.clock = clock;
	}

	@Transactional
	TokenPair open(SessionKind kind, UUID subjectId) {
		Instant now = Instant.now(clock);
		String access = Tokens.random(ACCESS_PREFIX);
		String refresh = Tokens.random(REFRESH_PREFIX);
		AuthSession session = sessions.save(new AuthSession(kind, subjectId, Tokens.sha256(access),
				Tokens.sha256(refresh), now, properties.sessionIdleTimeout(), properties.sessionAbsoluteTtl(),
				properties.accessTokenTtl()));
		return new TokenPair(access, refresh, session.accessRemaining(now));
	}

	@Override
	@Transactional
	public Optional<Caller> resolve(String bearerToken) {
		if (!bearerToken.startsWith(ACCESS_PREFIX)) {
			return Optional.empty();
		}
		Instant now = Instant.now(clock);
		return sessions.findByAccessTokenHash(Tokens.sha256(bearerToken))
			.filter(session -> session.acceptsAccessToken(now))
			.flatMap(session -> toCaller(session, now));
	}

	/**
	 * Rotates both tokens. A refresh token that was already rotated away and is used again has
	 * leaked: the whole session is ended.
	 */
	@Transactional
	Optional<TokenPair> refresh(String refreshToken, SessionKind kind) {
		if (refreshToken == null || !refreshToken.startsWith(REFRESH_PREFIX)) {
			return Optional.empty();
		}
		Instant now = Instant.now(clock);
		byte[] hash = Tokens.sha256(refreshToken);
		Optional<AuthSession> current = sessions.findByRefreshTokenHash(hash);
		if (current.isEmpty()) {
			sessions.findByPreviousRefreshHash(hash).ifPresent(session -> session.revoke(now, "REFRESH_REUSE"));
			return Optional.empty();
		}
		AuthSession session = current.get();
		if (!session.isActive(now) || session.getKind() != kind) {
			return Optional.empty();
		}
		String access = Tokens.random(ACCESS_PREFIX);
		String refresh = Tokens.random(REFRESH_PREFIX);
		session.rotate(Tokens.sha256(access), Tokens.sha256(refresh), now, properties.sessionIdleTimeout(),
				properties.accessTokenTtl());
		return Optional.of(new TokenPair(access, refresh, session.accessRemaining(now)));
	}

	@Transactional
	void revoke(UUID sessionId, String reason) {
		Instant now = Instant.now(clock);
		sessions.findById(sessionId).ifPresent(session -> session.revoke(now, reason));
	}

	private void touch(AuthSession session, Instant now) {
		if (session.touchDue(now)) {
			sessions.touch(session.getId(), now, now.plus(properties.sessionIdleTimeout()),
					AuthSession.touchedBefore(now));
		}
	}

	/** Staff roles come from the account on every request: a removed role or a disabled account counts at once. */
	private Optional<Caller> toCaller(AuthSession session, Instant now) {
		Set<Role> roles;
		if (session.getKind() == SessionKind.STAFF) {
			Optional<StaffUser> member = staff.findById(session.getSubjectId()).filter(StaffUser::isActive);
			if (member.isEmpty()) {
				session.revoke(now, "STAFF_NOT_ACTIVE");
				return Optional.empty();
			}
			roles = member.get().getRoles();
		}
		else {
			roles = Set.of(Role.PARTNER_APPLICANT);
		}
		touch(session, now);
		return Optional.of(new Caller(session.getId(), session.getKind(), session.getSubjectId(), roles));
	}
}
