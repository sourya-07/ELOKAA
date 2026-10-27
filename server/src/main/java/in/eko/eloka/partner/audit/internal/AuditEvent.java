package in.eko.eloka.partner.audit.internal;

import java.time.Instant;
import java.util.UUID;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.UuidGenerator;

/** One row of the audit trail. Immutable once written. */
@Entity
@Table(name = "audit_event")
class AuditEvent {

	@Id
	@UuidGenerator(style = UuidGenerator.Style.VERSION_7)
	@Column(columnDefinition = "BINARY(16)")
	private UUID id;

	@Column(nullable = false, unique = true)
	private long seq;

	@Column(name = "occurred_at", nullable = false)
	private Instant occurredAt;

	@Column(name = "actor_type", nullable = false, length = 20)
	private String actorType;

	@Column(name = "actor_id", length = 64)
	private String actorId;

	@Column(nullable = false, length = 64)
	private String action;

	@Column(name = "target_type", length = 40)
	private String targetType;

	@Column(name = "target_id", length = 64)
	private String targetId;

	@Column(length = 2000)
	private String details;

	@Column(name = "prev_hash", nullable = false, columnDefinition = "BINARY(32)")
	private byte[] prevHash;

	@Column(nullable = false, columnDefinition = "BINARY(32)")
	private byte[] hash;

	protected AuditEvent() {
	}

	AuditEvent(long seq, Instant occurredAt, String actorType, String actorId, String action,
			String targetType, String targetId, String details, byte[] prevHash, byte[] hash) {
		this.seq = seq;
		this.occurredAt = occurredAt;
		this.actorType = actorType;
		this.actorId = actorId;
		this.action = action;
		this.targetType = targetType;
		this.targetId = targetId;
		this.details = details;
		this.prevHash = prevHash;
		this.hash = hash;
	}
}
