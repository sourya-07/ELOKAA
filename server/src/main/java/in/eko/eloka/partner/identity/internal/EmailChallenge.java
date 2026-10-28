package in.eko.eloka.partner.identity.internal;

import java.security.MessageDigest;
import java.time.Instant;
import java.util.UUID;
import in.eko.eloka.partner.shared.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/**
 * One email code sent to one address for one application. Only a keyed hash of the code is kept;
 * the address is encrypted (it is returned once the code is right).
 */
@Entity
@Table(name = "email_verification_challenge")
class EmailChallenge extends BaseEntity {

	@Column(name = "subject_id", nullable = false, columnDefinition = "BINARY(16)")
	private UUID subjectId;

	@Column(name = "email_enc", nullable = false, columnDefinition = "VARBINARY(512)")
	private byte[] emailEnc;

	@Column(name = "email_hash", nullable = false, columnDefinition = "BINARY(32)")
	private byte[] emailHash;

	@Column(name = "code_hash", columnDefinition = "BINARY(32)")
	private byte[] codeHash;

	@Column(name = "client_ip_hash", columnDefinition = "BINARY(32)")
	private byte[] clientIpHash;

	@Column(name = "expires_at", nullable = false)
	private Instant expiresAt;

	@Column(nullable = false)
	private int attempts;

	@Column(name = "max_attempts", nullable = false)
	private int maxAttempts;

	@Column(name = "consumed_at")
	private Instant consumedAt;

	protected EmailChallenge() {
	}

	EmailChallenge(UUID subjectId, byte[] emailEnc, byte[] emailHash, byte[] clientIpHash, Instant expiresAt,
			int maxAttempts) {
		this.subjectId = subjectId;
		this.emailEnc = emailEnc;
		this.emailHash = emailHash;
		this.clientIpHash = clientIpHash;
		this.expiresAt = expiresAt;
		this.maxAttempts = maxAttempts;
	}

	/** The code's hash is set once the row has its id (the id is part of the hashed value). */
	void codeHash(byte[] hash) {
		this.codeHash = hash;
	}

	boolean isLive(Instant now) {
		return codeHash != null && consumedAt == null && now.isBefore(expiresAt) && attempts < maxAttempts;
	}

	/** Uses up one attempt before the code is compared. @return attempts left after this one */
	int reserveAttempt() {
		attempts++;
		return Math.max(0, maxAttempts - attempts);
	}

	boolean matches(byte[] candidateHash) {
		return codeHash != null && MessageDigest.isEqual(codeHash, candidateHash);
	}

	void consume(Instant now) {
		this.consumedAt = now;
	}

	UUID getSubjectId() {
		return subjectId;
	}

	byte[] getEmailEnc() {
		return emailEnc;
	}

	byte[] getEmailHash() {
		return emailHash;
	}

	Instant getExpiresAt() {
		return expiresAt;
	}
}
