package in.eko.eloka.partner.identity.internal;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import in.eko.eloka.partner.shared.persistence.BaseEntity;
import in.eko.eloka.partner.shared.security.SessionKind;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

/**
 * A sign-in session. Holds only hashes of its tokens. Owns its lifetime rules: short-lived access
 * token, sliding idle timeout, absolute lifetime, revocation, and token rotation.
 */
@Entity
@Table(name = "auth_session")
class AuthSession extends BaseEntity {

	/** Do not rewrite last_seen_at more often than this (keeps reads cheap). */
	private static final Duration TOUCH_INTERVAL = Duration.ofSeconds(60);

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private SessionKind kind;

	/** Applicant id or staff user id. */
	@Column(name = "subject_id", nullable = false, columnDefinition = "BINARY(16)")
	private UUID subjectId;

	@Column(name = "access_token_hash", nullable = false, unique = true, columnDefinition = "BINARY(32)")
	private byte[] accessTokenHash;

	@Column(name = "refresh_token_hash", nullable = false, unique = true, columnDefinition = "BINARY(32)")
	private byte[] refreshTokenHash;

	@Column(name = "previous_refresh_hash", columnDefinition = "BINARY(32)")
	private byte[] previousRefreshHash;

	@Column(name = "access_expires_at", nullable = false)
	private Instant accessExpiresAt;

	@Column(name = "idle_expires_at", nullable = false)
	private Instant idleExpiresAt;

	@Column(name = "absolute_expires_at", nullable = false)
	private Instant absoluteExpiresAt;

	@Column(name = "last_seen_at", nullable = false)
	private Instant lastSeenAt;

	@Column(name = "revoked_at")
	private Instant revokedAt;

	@Column(name = "revoke_reason", length = 40)
	private String revokeReason;

	protected AuthSession() {
	}

	AuthSession(SessionKind kind, UUID subjectId, byte[] accessTokenHash, byte[] refreshTokenHash, Instant now,
			Duration idleTimeout, Duration absoluteTtl, Duration accessTtl) {
		this.kind = kind;
		this.subjectId = subjectId;
		this.accessTokenHash = accessTokenHash;
		this.refreshTokenHash = refreshTokenHash;
		this.lastSeenAt = now;
		this.absoluteExpiresAt = now.plus(absoluteTtl);
		this.idleExpiresAt = capped(now.plus(idleTimeout));
		this.accessExpiresAt = capped(now.plus(accessTtl));
	}

	boolean isActive(Instant now) {
		return revokedAt == null && now.isBefore(idleExpiresAt) && now.isBefore(absoluteExpiresAt);
	}

	/** The access token works only while the session is active and before its own expiry. */
	boolean acceptsAccessToken(Instant now) {
		return isActive(now) && now.isBefore(accessExpiresAt);
	}

	/**
	 * Whether a use at {@code now} should slide the idle window. The write itself is a guarded
	 * update ({@code AuthSessionRepository.touch}) so parallel requests do not conflict.
	 */
	boolean touchDue(Instant now) {
		return !lastSeenAt.plus(TOUCH_INTERVAL).isAfter(now);
	}

	/** Rows last seen before this instant are due a touch. */
	static Instant touchedBefore(Instant now) {
		return now.minus(TOUCH_INTERVAL);
	}

	void rotate(byte[] newAccessHash, byte[] newRefreshHash, Instant now, Duration idleTimeout, Duration accessTtl) {
		this.previousRefreshHash = this.refreshTokenHash;
		this.accessTokenHash = newAccessHash;
		this.refreshTokenHash = newRefreshHash;
		this.lastSeenAt = now;
		this.idleExpiresAt = capped(now.plus(idleTimeout));
		this.accessExpiresAt = capped(now.plus(accessTtl));
	}

	void revoke(Instant now, String reason) {
		if (revokedAt == null) {
			revokedAt = now;
			revokeReason = reason;
		}
	}

	SessionKind getKind() {
		return kind;
	}

	UUID getSubjectId() {
		return subjectId;
	}

	/** Lifetime left on the current access token. */
	Duration accessRemaining(Instant now) {
		return Duration.between(now, accessExpiresAt);
	}

	private Instant capped(Instant end) {
		return end.isBefore(absoluteExpiresAt) ? end : absoluteExpiresAt;
	}
}
