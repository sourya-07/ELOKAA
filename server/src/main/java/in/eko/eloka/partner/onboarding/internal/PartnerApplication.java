package in.eko.eloka.partner.onboarding.internal;

import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import in.eko.eloka.partner.onboarding.ApplicationStatus;
import in.eko.eloka.partner.onboarding.VerificationMethod;
import in.eko.eloka.partner.shared.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

/**
 * One partner application. Status changes only through {@link ApplicationStateMachine}; this
 * class keeps the data consistent with the status.
 */
@Entity
@Table(name = "partner_application")
class PartnerApplication extends BaseEntity {

	@Column(name = "public_id", nullable = false, unique = true, length = 32)
	private String publicId;

	@Column(name = "applicant_id", nullable = false, columnDefinition = "BINARY(16)")
	private UUID applicantId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 30)
	private ApplicationStatus status;

	@Enumerated(EnumType.STRING)
	@Column(length = 20)
	private VerificationMethod method;

	/** Blind index of the verified PAN; unique among applications that block a new one. */
	@Column(name = "pan_hash", columnDefinition = "BINARY(32)")
	private byte[] panHash;

	/** The verified email address (encrypted), its blind index (one live application per address) and a masked copy. */
	@Column(name = "email_enc", columnDefinition = "VARBINARY(512)")
	private byte[] emailEnc;

	@Column(name = "email_hash", columnDefinition = "BINARY(32)")
	private byte[] emailHash;

	@Column(name = "email_masked", length = 80)
	private String emailMasked;

	@Column(name = "assigned_staff_id", columnDefinition = "BINARY(16)")
	private UUID assignedStaffId;

	@Column(name = "correction_due_at")
	private Instant correctionDueAt;

	@Column(name = "last_applicant_activity_at", nullable = false)
	private Instant lastApplicantActivityAt;

	@Column(name = "expires_at", nullable = false)
	private Instant expiresAt;

	@Column(name = "status_changed_at", nullable = false)
	private Instant statusChangedAt;

	@Column(name = "submitted_at")
	private Instant submittedAt;

	@Column(name = "closed_at")
	private Instant closedAt;

	protected PartnerApplication() {
	}

	PartnerApplication(String publicId, UUID applicantId, Instant now, Duration idleExpiry) {
		this.publicId = publicId;
		this.applicantId = applicantId;
		this.status = ApplicationStatus.IN_PROGRESS;
		this.statusChangedAt = now;
		recordActivity(now, idleExpiry);
	}

	/** Every applicant write extends the idle window. An open correction keeps its own due date. */
	void recordActivity(Instant now, Duration idleExpiry) {
		this.lastApplicantActivityAt = now;
		this.expiresAt = correctionDueAt != null ? correctionDueAt : now.plus(idleExpiry);
	}

	/** Called by {@link ApplicationStateMachine} only. */
	void moveTo(ApplicationStatus next, Instant now) {
		this.status = next;
		this.statusChangedAt = now;
		if (next == ApplicationStatus.SUBMITTED && submittedAt == null) {
			submittedAt = now;
		}
		if (next.isTerminal()) {
			closedAt = now;
		}
	}

	void chooseMethod(VerificationMethod chosen) {
		this.method = chosen;
	}

	void bindPan(byte[] hash) {
		this.panHash = hash;
	}

	void bindEmail(byte[] encrypted, byte[] hash, String masked) {
		this.emailEnc = encrypted;
		this.emailHash = hash;
		this.emailMasked = masked;
	}

	String getEmailMasked() {
		return emailMasked;
	}

	byte[] getEmailEnc() {
		return emailEnc;
	}

	boolean isBoundTo(byte[] hash) {
		return panHash != null && MessageDigest.isEqual(panHash, hash);
	}

	void assignTo(UUID staffId) {
		this.assignedStaffId = staffId;
	}

	/** Opens ({@code dueAt}) or closes ({@code null}) a correction; an open one expires on its due date. */
	void correctionDue(Instant dueAt) {
		this.correctionDueAt = dueAt;
		if (dueAt != null) {
			this.expiresAt = dueAt;
		}
	}

	boolean isIdleSince(Instant cutoff) {
		return lastApplicantActivityAt.isBefore(cutoff);
	}

	boolean isCorrectionOverdue(Instant now) {
		return status == ApplicationStatus.CORRECTION_REQUIRED && correctionDueAt != null && correctionDueAt.isBefore(now);
	}

	String getPublicId() {
		return publicId;
	}

	UUID getApplicantId() {
		return applicantId;
	}

	ApplicationStatus getStatus() {
		return status;
	}

	VerificationMethod getMethod() {
		return method;
	}

	UUID getAssignedStaffId() {
		return assignedStaffId;
	}

	Instant getExpiresAt() {
		return expiresAt;
	}

	Instant getSubmittedAt() {
		return submittedAt;
	}
}
