package in.eko.eloka.partner.verification.internal;

import java.time.Instant;
import java.util.UUID;
import in.eko.eloka.partner.shared.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/**
 * One trip to DigiLocker. The applicant comes back with a random state; only its SHA-256 is
 * stored. It belongs to one application, works once and only for 15 minutes.
 */
@Entity
@Table(name = "digilocker_session")
class DigiLockerSession extends BaseEntity {

	@Column(name = "application_id", nullable = false, columnDefinition = "BINARY(16)")
	private UUID applicationId;

	@Column(name = "verification_ref", nullable = false, unique = true, length = 20)
	private String verificationRef;

	@Column(name = "provider_ref", nullable = false, length = 64)
	private String providerRef;

	@Column(name = "state_hash", nullable = false, unique = true, columnDefinition = "BINARY(32)")
	private byte[] stateHash;

	@Column(name = "expires_at", nullable = false)
	private Instant expiresAt;

	@Column(name = "consumed_at")
	private Instant consumedAt;

	protected DigiLockerSession() {
	}

	DigiLockerSession(UUID applicationId, String verificationRef, String providerRef, byte[] stateHash,
			Instant expiresAt) {
		this.applicationId = applicationId;
		this.verificationRef = verificationRef;
		this.providerRef = providerRef;
		this.stateHash = stateHash;
		this.expiresAt = expiresAt;
	}

	boolean isExpired(Instant now) {
		return !now.isBefore(expiresAt);
	}

	boolean isConsumed() {
		return consumedAt != null;
	}

	void consume(Instant now) {
		this.consumedAt = now;
	}

	UUID getApplicationId() {
		return applicationId;
	}

	String getVerificationRef() {
		return verificationRef;
	}

	String getProviderRef() {
		return providerRef;
	}
}
