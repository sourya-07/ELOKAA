package in.eko.eloka.partner.verification.internal;

import java.time.Instant;
import java.util.UUID;
import in.eko.eloka.partner.shared.persistence.BaseEntity;
import in.eko.eloka.partner.verification.EvidenceStatus;
import in.eko.eloka.partner.verification.VerificationApi;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

/** One identity check attempt and its evidence status. Personal values are encrypted. */
@Entity
@Table(name = "verification")
class Verification extends BaseEntity {

	@Column(name = "application_id", nullable = false, columnDefinition = "BINARY(16)")
	private UUID applicationId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private VerificationApi.Kind kind;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private VerificationApi.Method method;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 30)
	private EvidenceStatus status;

	@Column(length = 30)
	private String provider;

	@Column(name = "provider_ref", length = 64)
	private String providerRef;

	@Column(name = "value_enc", columnDefinition = "VARBINARY(128)")
	private byte[] valueEnc;

	@Column(name = "value_hash", columnDefinition = "BINARY(32)")
	private byte[] valueHash;

	@Column(name = "value_masked", length = 20)
	private String valueMasked;

	@Column(name = "name_enc", columnDefinition = "VARBINARY(512)")
	private byte[] nameEnc;

	@Column(name = "dob_enc", columnDefinition = "VARBINARY(64)")
	private byte[] dobEnc;

	@Column(name = "failure_code", length = 40)
	private String failureCode;

	@Column(name = "decided_at")
	private Instant decidedAt;

	protected Verification() {
	}

	Verification(UUID applicationId, VerificationApi.Kind kind, VerificationApi.Method method, EvidenceStatus status,
			String provider, String providerRef) {
		this.applicationId = applicationId;
		this.kind = kind;
		this.method = method;
		this.status = status;
		this.provider = provider;
		this.providerRef = providerRef;
	}

	Verification value(byte[] encrypted, byte[] hash, String masked) {
		this.valueEnc = encrypted;
		this.valueHash = hash;
		this.valueMasked = masked;
		return this;
	}

	Verification person(byte[] encryptedName, byte[] encryptedDob) {
		this.nameEnc = encryptedName;
		this.dobEnc = encryptedDob;
		return this;
	}

	Verification failed(String code) {
		this.status = EvidenceStatus.VERIFICATION_FAILED;
		this.failureCode = code;
		return this;
	}

	/** A person's decision on evidence that needs one. */
	void decide(boolean verified, String reasonCode, Instant now) {
		this.status = verified ? EvidenceStatus.VERIFIED : EvidenceStatus.REJECTED;
		this.failureCode = verified ? null : reasonCode;
		this.decidedAt = now;
	}

	UUID getApplicationId() {
		return applicationId;
	}

	VerificationApi.Kind getKind() {
		return kind;
	}

	VerificationApi.Method getMethod() {
		return method;
	}

	EvidenceStatus getStatus() {
		return status;
	}

	byte[] getValueEnc() {
		return valueEnc;
	}

	String getValueMasked() {
		return valueMasked;
	}

	byte[] getNameEnc() {
		return nameEnc;
	}

	byte[] getDobEnc() {
		return dobEnc;
	}

	String getFailureCode() {
		return failureCode;
	}

	VerificationApi.Evidence toEvidence() {
		return new VerificationApi.Evidence(getId(), kind, method, status, valueMasked, failureCode);
	}
}
