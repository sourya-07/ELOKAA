package in.eko.eloka.partner.eligibility.internal;

import java.time.Instant;
import java.util.UUID;
import in.eko.eloka.partner.eligibility.EligibilityOutcome;
import in.eko.eloka.partner.shared.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

/** One credit assessment. Only the score is kept, never the bureau report. Immutable once written. */
@Entity
@Table(name = "credit_assessment")
class CreditAssessment extends BaseEntity {

	@Column(name = "application_id", nullable = false, columnDefinition = "BINARY(16)")
	private UUID applicationId;

	@Column(name = "pan_hash", nullable = false, columnDefinition = "BINARY(32)")
	private byte[] panHash;

	@Column(nullable = false, length = 30)
	private String provider;

	@Column(name = "provider_ref", length = 64)
	private String providerRef;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private EligibilityOutcome outcome;

	@Column(columnDefinition = "SMALLINT")
	private Integer score;

	@Column(name = "failure_code", length = 40)
	private String failureCode;

	@Column(name = "assessed_at", nullable = false)
	private Instant assessedAt;

	protected CreditAssessment() {
	}

	CreditAssessment(UUID applicationId, byte[] panHash, String provider, String providerRef,
			EligibilityOutcome outcome, Integer score, String failureCode, Instant assessedAt) {
		this.applicationId = applicationId;
		this.panHash = panHash;
		this.provider = provider;
		this.providerRef = providerRef;
		this.outcome = outcome;
		this.score = score;
		this.failureCode = failureCode;
		this.assessedAt = assessedAt;
	}

	byte[] getPanHash() {
		return panHash;
	}

	Integer getScore() {
		return score;
	}

	String getProviderRef() {
		return providerRef;
	}

	EligibilityOutcome getOutcome() {
		return outcome;
	}

	Instant getAssessedAt() {
		return assessedAt;
	}
}
