package in.eko.eloka.partner.onboarding.internal;

import java.time.Instant;
import java.util.UUID;
import in.eko.eloka.partner.shared.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

/** Proof of what the applicant agreed to, for which purpose and when (append-only, DPDP Act). */
@Entity
@Table(name = "consent_record")
class ConsentRecord extends BaseEntity {

	/** Each purpose is agreed to separately. */
	enum Purpose {
		/** Processing the application data (page 2). */
		DATA_PROCESSING,
		/** Fetching identity and address from DigiLocker. */
		DIGILOCKER,
		/** Asking a credit bureau for the CIBIL score. */
		CREDIT_CHECK
	}

	@Column(name = "application_id", nullable = false, columnDefinition = "BINARY(16)")
	private UUID applicationId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private Purpose purpose;

	@Column(name = "text_version", nullable = false, length = 20)
	private String textVersion;

	@Column(name = "accepted_at", nullable = false)
	private Instant acceptedAt;

	@Column(name = "client_ip_hash", columnDefinition = "BINARY(32)")
	private byte[] clientIpHash;

	protected ConsentRecord() {
	}

	ConsentRecord(UUID applicationId, Purpose purpose, String textVersion, Instant acceptedAt, byte[] clientIpHash) {
		this.applicationId = applicationId;
		this.purpose = purpose;
		this.textVersion = textVersion;
		this.acceptedAt = acceptedAt;
		this.clientIpHash = clientIpHash;
	}

	Purpose getPurpose() {
		return purpose;
	}

	String getTextVersion() {
		return textVersion;
	}
}
