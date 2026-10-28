package in.eko.eloka.partner.onboarding.internal;

import java.time.Instant;
import java.util.UUID;
import in.eko.eloka.partner.documents.DocumentKind;
import in.eko.eloka.partner.shared.persistence.BaseEntity;
import in.eko.eloka.partner.verification.EvidenceStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

/** A stored file attached to an application, with its review status. */
@Entity
@Table(name = "application_document")
class ApplicationDocument extends BaseEntity {

	@Column(name = "application_id", nullable = false, columnDefinition = "BINARY(16)")
	private UUID applicationId;

	@Column(name = "document_id", nullable = false, columnDefinition = "BINARY(16)")
	private UUID documentId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 30)
	private DocumentKind kind;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 30)
	private EvidenceStatus status;

	@Column(name = "reject_reason", length = 40)
	private String rejectReason;

	@Column(name = "removed_at")
	private Instant removedAt;

	@Column(name = "decided_at")
	private Instant decidedAt;

	protected ApplicationDocument() {
	}

	/** Uploaded files wait for a person: none of them is verified by being submitted. */
	ApplicationDocument(UUID applicationId, UUID documentId, DocumentKind kind) {
		this.applicationId = applicationId;
		this.documentId = documentId;
		this.kind = kind;
		this.status = EvidenceStatus.MANUAL_REVIEW_REQUIRED;
	}

	boolean isActive() {
		return removedAt == null;
	}

	/** Replaced by a newer upload, or removed by the applicant. The file stays for the audit trail. */
	void remove(Instant now) {
		this.removedAt = now;
	}

	/** What it shows may no longer fit (a new PAN, another ID document): a reviewer looks again. */
	void resetDecision() {
		this.status = EvidenceStatus.MANUAL_REVIEW_REQUIRED;
		this.rejectReason = null;
		this.decidedAt = null;
	}

	void decide(boolean verified, String reasonCode, Instant now) {
		this.status = verified ? EvidenceStatus.VERIFIED : EvidenceStatus.REJECTED;
		this.rejectReason = verified ? null : reasonCode;
		this.decidedAt = now;
	}

	UUID getApplicationId() {
		return applicationId;
	}

	UUID getDocumentId() {
		return documentId;
	}

	DocumentKind getKind() {
		return kind;
	}

	EvidenceStatus getStatus() {
		return status;
	}

	String getRejectReason() {
		return rejectReason;
	}
}
