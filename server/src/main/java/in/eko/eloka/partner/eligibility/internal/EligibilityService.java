package in.eko.eloka.partner.eligibility.internal;

import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import in.eko.eloka.partner.eligibility.EligibilityApi;
import in.eko.eloka.partner.eligibility.EligibilityOutcome;
import in.eko.eloka.partner.eligibility.EligibilityPolicy;
import in.eko.eloka.partner.integration.CreditBureau;
import in.eko.eloka.partner.integration.ProviderUnavailableException;
import in.eko.eloka.partner.shared.crypto.BlindIndex;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

interface CreditAssessmentRepository extends JpaRepository<CreditAssessment, UUID> {

	List<CreditAssessment> findByApplicationIdOrderByAssessedAtDesc(UUID applicationId);
}

/**
 * Runs credit assessments. A definite result (eligible or not) for the same PAN in the same
 * application is reused for 30 days, so redoing an earlier step does not query the bureau again.
 */
@Service
class EligibilityService implements EligibilityApi {

	private static final Logger log = LoggerFactory.getLogger(EligibilityService.class);
	private static final Duration REUSE_WINDOW = Duration.ofDays(30);

	private final CreditAssessmentRepository assessments;
	private final CreditBureau bureau;
	private final BlindIndex index;
	private final Clock clock;

	EligibilityService(CreditAssessmentRepository assessments, CreditBureau bureau, BlindIndex index, Clock clock) {
		this.assessments = assessments;
		this.bureau = bureau;
		this.index = index;
		this.clock = clock;
	}

	@Override
	@Transactional
	public Assessment assess(UUID applicationId, String pan, String name, LocalDate dob) {
		Instant now = Instant.now(clock);
		byte[] panHash = index.hash("pan", pan);
		Optional<CreditAssessment> recent = assessments.findByApplicationIdOrderByAssessedAtDesc(applicationId)
			.stream()
			.filter(a -> a.getOutcome() == EligibilityOutcome.ELIGIBLE || a.getOutcome() == EligibilityOutcome.INELIGIBLE)
			.filter(a -> MessageDigest.isEqual(a.getPanHash(), panHash))
			.filter(a -> a.getAssessedAt().isAfter(now.minus(REUSE_WINDOW)))
			.findFirst();
		if (recent.isPresent()) {
			// Recorded again, so the latest row always states the application's current outcome
			CreditAssessment earlier = recent.get();
			assessments.save(new CreditAssessment(applicationId, panHash, "reused", earlier.getProviderRef(),
					earlier.getOutcome(), earlier.getScore(), null, now));
			return new Assessment(earlier.getOutcome(), null, true);
		}
		CreditBureau.Report report;
		try {
			report = bureau.fetchScore(new CreditBureau.Request(pan, name, dob));
		}
		catch (ProviderUnavailableException ex) {
			assessments.save(new CreditAssessment(applicationId, panHash, "bureau", null,
					EligibilityOutcome.UNABLE_TO_ASSESS, null, "BUREAU_UNAVAILABLE", now));
			return new Assessment(EligibilityOutcome.UNABLE_TO_ASSESS, "BUREAU_UNAVAILABLE", false);
		}
		EligibilityOutcome outcome = EligibilityPolicy.decide(report.score());
		String failure = null;
		if (outcome == EligibilityOutcome.UNABLE_TO_ASSESS) {
			failure = report.noHit() ? "NO_CREDIT_HISTORY" : "INVALID_SCORE";
			log.warn("Credit assessment could not decide: {}", failure);
		}
		// An out-of-range answer is not a score: keep only the fact that it was invalid
		Integer kept = outcome == EligibilityOutcome.UNABLE_TO_ASSESS ? null : report.score();
		assessments.save(new CreditAssessment(applicationId, panHash, "bureau", report.providerRef(), outcome, kept,
				failure, now));
		return new Assessment(outcome, failure, false);
	}

	@Override
	@Transactional(readOnly = true)
	public EligibilityOutcome latestOutcome(UUID applicationId) {
		return assessments.findByApplicationIdOrderByAssessedAtDesc(applicationId)
			.stream()
			.findFirst()
			.map(CreditAssessment::getOutcome)
			.orElse(EligibilityOutcome.PENDING);
	}
}
