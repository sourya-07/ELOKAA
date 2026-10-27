package in.eko.eloka.partner.onboarding.internal;

import java.time.Instant;
import java.util.UUID;
import in.eko.eloka.partner.onboarding.PartnerStep;
import in.eko.eloka.partner.onboarding.StepStatus;
import in.eko.eloka.partner.shared.error.ApiProblem;
import in.eko.eloka.partner.shared.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.http.HttpStatus;

/** One step of one application, with its own status rules. */
@Entity
@Table(name = "application_step")
class ApplicationStep extends BaseEntity {

	@Column(name = "application_id", nullable = false, columnDefinition = "BINARY(16)")
	private UUID applicationId;

	@Enumerated(EnumType.STRING)
	@Column(name = "step_key", nullable = false, length = 30)
	private PartnerStep stepKey;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private StepStatus status;

	@Column(name = "attempt_count", nullable = false)
	private int attemptCount;

	@Column(name = "max_attempts", nullable = false)
	private int maxAttempts;

	@Column(name = "last_error_code", length = 40)
	private String lastErrorCode;

	/** Applicant-safe view of the result (masked values only). */
	@JdbcTypeCode(SqlTypes.JSON)
	@Column(columnDefinition = "JSON")
	private String summary;

	@Column(name = "reopen_reason_code", length = 40)
	private String reopenReasonCode;

	@Column(name = "reviewer_message", length = 1000)
	private String reviewerMessage;

	@Column(name = "completed_at")
	private Instant completedAt;

	protected ApplicationStep() {
	}

	ApplicationStep(UUID applicationId, PartnerStep stepKey, int maxAttempts) {
		this.applicationId = applicationId;
		this.stepKey = stepKey;
		this.maxAttempts = maxAttempts;
		this.status = StepStatus.NOT_STARTED;
	}

	/** Checked and accepted. */
	void complete(String summaryJson, Instant now) {
		requireAttemptAllowed();
		this.status = StepStatus.COMPLETED;
		this.summary = summaryJson;
		this.lastErrorCode = null;
		this.completedAt = now;
		this.reopenReasonCode = null;
		this.reviewerMessage = null;
	}

	/** Waiting for a provider (redirect, async check). */
	void awaitExternal() {
		requireAttemptAllowed();
		this.status = StepStatus.PENDING_EXTERNAL;
		this.lastErrorCode = null;
	}

	/** A failed verification attempt; the step blocks after {@code maxAttempts} failures. */
	void fail(String errorCode) {
		requireAttemptAllowed();
		this.attemptCount++;
		this.lastErrorCode = errorCode;
		this.status = attemptCount >= maxAttempts ? StepStatus.BLOCKED : StepStatus.FAILED;
	}

	/** A technical failure that is nobody's fault: recorded, but it does not use up an attempt. */
	void failTechnically(String errorCode) {
		requireAttemptAllowed();
		this.lastErrorCode = errorCode;
		this.status = StepStatus.FAILED;
	}

	/** An upstream step changed, so this result is stale. */
	void invalidate() {
		if (status == StepStatus.COMPLETED || status == StepStatus.PENDING_EXTERNAL) {
			status = StepStatus.IN_PROGRESS;
			completedAt = null;
		}
	}

	/** New data arrived for a step that was done or reopened; it must be submitted again. */
	void markInProgress() {
		if (status == StepStatus.REOPENED || status == StepStatus.COMPLETED || status == StepStatus.NOT_STARTED) {
			status = StepStatus.IN_PROGRESS;
			completedAt = null;
		}
	}

	/** Application submitted: completed data is frozen. */
	void lock() {
		if (status == StepStatus.COMPLETED) {
			status = StepStatus.LOCKED;
		}
	}

	/**
	 * A reviewer asks for this step again; the applicant sees the reason and message.
	 *
	 * @return false when the step was never done, so there is nothing to reopen
	 */
	boolean reopen(String reasonCode, String message) {
		if (status == StepStatus.LOCKED || status == StepStatus.COMPLETED) {
			this.status = StepStatus.REOPENED;
			this.reopenReasonCode = reasonCode;
			this.reviewerMessage = message;
			return true;
		}
		return false;
	}

	private void requireAttemptAllowed() {
		if (status == StepStatus.BLOCKED) {
			throw new ApiProblem(HttpStatus.LOCKED, "STEP_BLOCKED",
					"Too many failed attempts on this step. Please contact support.");
		}
		if (status == StepStatus.LOCKED || status == StepStatus.NOT_APPLICABLE) {
			throw new ApiProblem(HttpStatus.FORBIDDEN, "STEP_NOT_EDITABLE", "This step cannot be changed now");
		}
	}

	UUID getApplicationId() {
		return applicationId;
	}

	PartnerStep getStepKey() {
		return stepKey;
	}

	StepStatus getStatus() {
		return status;
	}

	int getAttemptCount() {
		return attemptCount;
	}

	int getMaxAttempts() {
		return maxAttempts;
	}

	String getLastErrorCode() {
		return lastErrorCode;
	}

	String getSummary() {
		return summary;
	}

	String getReopenReasonCode() {
		return reopenReasonCode;
	}

	String getReviewerMessage() {
		return reviewerMessage;
	}
}
