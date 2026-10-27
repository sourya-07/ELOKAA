package in.eko.eloka.partner.onboarding.internal;

import java.time.Instant;
import java.util.UUID;
import in.eko.eloka.partner.shared.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/** The first response to an {@code Idempotency-Key}, replayed for 24 hours. */
@Entity
@Table(name = "idempotency_record")
class IdempotencyRecord extends BaseEntity {

	@Column(name = "scope_id", nullable = false, columnDefinition = "BINARY(16)")
	private UUID scopeId;

	@Column(name = "idem_key", nullable = false, length = 80)
	private String idemKey;

	@Column(name = "request_hash", nullable = false, columnDefinition = "BINARY(32)")
	private byte[] requestHash;

	@Column(name = "response_status")
	private Integer responseStatus;

	@Column(name = "response_etag", length = 40)
	private String responseEtag;

	@Column(name = "response_body", columnDefinition = "MEDIUMTEXT")
	private String responseBody;

	@Column(name = "expires_at", nullable = false)
	private Instant expiresAt;

	protected IdempotencyRecord() {
	}

	IdempotencyRecord(UUID scopeId, String idemKey, byte[] requestHash, Instant expiresAt) {
		this.scopeId = scopeId;
		this.idemKey = idemKey;
		this.requestHash = requestHash;
		this.expiresAt = expiresAt;
	}

	void complete(int status, String etag, String body) {
		this.responseStatus = status;
		this.responseEtag = etag;
		this.responseBody = body;
	}

	boolean isComplete() {
		return responseStatus != null;
	}

	boolean isExpired(Instant now) {
		return !now.isBefore(expiresAt);
	}

	byte[] getRequestHash() {
		return requestHash;
	}

	Integer getResponseStatus() {
		return responseStatus;
	}

	String getResponseEtag() {
		return responseEtag;
	}

	String getResponseBody() {
		return responseBody;
	}
}
