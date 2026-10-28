package in.eko.eloka.partner.onboarding.internal;

import java.time.Instant;
import java.util.UUID;
import in.eko.eloka.partner.onboarding.ApplicationStatus;
import in.eko.eloka.partner.onboarding.PartnerStep;
import in.eko.eloka.partner.shared.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

/** An applicant-safe event in the history of an application. The full trail is the audit log. */
@Entity
@Table(name = "application_event")
class TimelineEntry extends BaseEntity {

	@Column(name = "application_id", nullable = false, columnDefinition = "BINARY(16)")
	private UUID applicationId;

	@Column(name = "event_type", nullable = false, length = 40)
	private String eventType;

	/** A plain string, not the enum: old rows keep naming steps that a later journey version retired. */
	@Column(name = "step_key", length = 30)
	private String stepKey;

	@Enumerated(EnumType.STRING)
	@Column(name = "from_status", length = 30)
	private ApplicationStatus fromStatus;

	@Enumerated(EnumType.STRING)
	@Column(name = "to_status", length = 30)
	private ApplicationStatus toStatus;

	@Column(nullable = false, length = 20)
	private String actor;

	@Column(name = "occurred_at", nullable = false)
	private Instant occurredAt;

	protected TimelineEntry() {
	}

	TimelineEntry(UUID applicationId, String eventType, PartnerStep stepKey, ApplicationStatus fromStatus,
			ApplicationStatus toStatus, String actor, Instant occurredAt) {
		this.applicationId = applicationId;
		this.eventType = eventType;
		this.stepKey = stepKey == null ? null : stepKey.name();
		this.fromStatus = fromStatus;
		this.toStatus = toStatus;
		this.actor = actor;
		this.occurredAt = occurredAt;
	}

	String getEventType() {
		return eventType;
	}

	String getStepKey() {
		return stepKey;
	}

	ApplicationStatus getToStatus() {
		return toStatus;
	}

	Instant getOccurredAt() {
		return occurredAt;
	}
}
