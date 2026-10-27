package in.eko.eloka.partner.onboarding.internal;

import java.time.Instant;
import java.util.UUID;
import in.eko.eloka.partner.shared.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/** One thing a staff member did on an application. Append-only; the maker-checker rule reads it. */
@Entity
@Table(name = "review_action")
class ReviewAction extends BaseEntity {

	static final String RECOMMEND = "RECOMMEND";

	@Column(name = "application_id", nullable = false, columnDefinition = "BINARY(16)")
	private UUID applicationId;

	@Column(name = "staff_id", nullable = false, columnDefinition = "BINARY(16)")
	private UUID staffId;

	@Column(nullable = false, length = 30)
	private String action;

	@Column(length = 20)
	private String decision;

	@Column(name = "reason_code", length = 40)
	private String reasonCode;

	@Column(length = 1000)
	private String note;

	@Column(name = "target_ref", length = 40)
	private String targetRef;

	@Column(name = "occurred_at", nullable = false)
	private Instant occurredAt;

	protected ReviewAction() {
	}

	ReviewAction(UUID applicationId, UUID staffId, String action, String decision, String reasonCode, String note,
			String targetRef, Instant occurredAt) {
		this.applicationId = applicationId;
		this.staffId = staffId;
		this.action = action;
		this.decision = decision;
		this.reasonCode = reasonCode;
		this.note = note;
		this.targetRef = targetRef;
		this.occurredAt = occurredAt;
	}

	UUID getStaffId() {
		return staffId;
	}

	String getAction() {
		return action;
	}

	String getDecision() {
		return decision;
	}

	String getReasonCode() {
		return reasonCode;
	}

	String getNote() {
		return note;
	}

	String getTargetRef() {
		return targetRef;
	}

	Instant getOccurredAt() {
		return occurredAt;
	}
}
