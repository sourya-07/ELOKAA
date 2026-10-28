package in.eko.eloka.partner.onboarding.internal;

import java.util.Map;
import java.util.UUID;
import in.eko.eloka.partner.audit.AuditEntry;
import in.eko.eloka.partner.audit.AuditLog;
import in.eko.eloka.partner.eligibility.EligibilityApi;
import in.eko.eloka.partner.onboarding.PartnerStep;
import in.eko.eloka.partner.onboarding.StepStatus;
import in.eko.eloka.partner.shared.error.ApiProblem;
import in.eko.eloka.partner.verification.VerificationApi;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/**
 * The credit check, right after the PAN (ADR-0005). Needs its own consent. The person checked is
 * the one behind the verified PAN; nothing the client sends about the person is used.
 * <ul>
 * <li>Eligible: the step completes (and, on the DigiLocker path, the home address from DigiLocker).</li>
 * <li>Not eligible: the application closes as {@code INELIGIBLE}.</li>
 * <li>Could not assess: the step fails and uses one attempt; after the last one it is blocked for staff.</li>
 * </ul>
 */
@Service
class EligibilityStep {

	private final ApplicationService applications;
	private final IdentitySteps identity;
	private final EligibilityApi eligibility;
	private final VerificationApi verification;
	private final ConsentRecordRepository consents;
	private final ApplicationStateMachine stateMachine;
	private final OnboardingProperties properties;
	private final AuditLog audit;
	private final JsonMapper json;

	EligibilityStep(ApplicationService applications, IdentitySteps identity, EligibilityApi eligibility,
			VerificationApi verification, ConsentRecordRepository consents, ApplicationStateMachine stateMachine,
			OnboardingProperties properties, AuditLog audit, JsonMapper json) {
		this.applications = applications;
		this.identity = identity;
		this.eligibility = eligibility;
		this.verification = verification;
		this.consents = consents;
		this.stateMachine = stateMachine;
		this.properties = properties;
		this.audit = audit;
		this.json = json;
	}

	@Transactional
	ApplicationSnapshot check(UUID applicantId, String publicId, long version) {
		applications.write(applicantId, publicId, version, context -> {
			context.requirePrerequisites(PartnerStep.CREDIT_CHECK);
			if (context.step(PartnerStep.CREDIT_CHECK).getStatus() == StepStatus.BLOCKED) {
				throw new ApiProblem(HttpStatus.LOCKED, "STEP_BLOCKED",
						"We could not complete your credit check. Our team will contact you.");
			}
			boolean consented = consents
				.findFirstByApplicationIdAndPurposeOrderByAcceptedAtDesc(context.applicationId(), ConsentRecord.Purpose.CREDIT_CHECK)
				.filter(consent -> consent.getTextVersion().equals(properties.consentVersion(ConsentRecord.Purpose.CREDIT_CHECK)))
				.isPresent();
			if (!consented) {
				throw new ApiProblem(HttpStatus.CONFLICT, "CONSENT_REQUIRED",
						"Allow ELOKA to get your credit information first", Map.of("purpose", "CREDIT_CHECK"));
			}
			VerificationApi.Identity person = verification.verifiedIdentity(context.applicationId())
				.orElseThrow(() -> new ApiProblem(HttpStatus.CONFLICT, "PREREQUISITE_INCOMPLETE",
						"Verify your PAN first", Map.of("missing_steps", java.util.List.of("PAN"))));
			EligibilityApi.Assessment assessment = eligibility.assess(context.applicationId(), person.pan(), person.name(),
					person.dob());
			Map<String, String> details = new java.util.LinkedHashMap<>();
			details.put("outcome", assessment.outcome().name());
			details.put("reused", Boolean.toString(assessment.reused()));
			if (assessment.failureCode() != null) {
				details.put("failure_code", assessment.failureCode());
			}
			audit.record(new AuditEntry("CREDIT_ASSESSED", "APPLICANT", applicantId.toString(), "PARTNER_APPLICATION",
					publicId, details)); // the outcome only, never the score
			switch (assessment.outcome()) {
				case ELIGIBLE -> {
					context.complete(PartnerStep.CREDIT_CHECK, json.writeValueAsString(Map.of("outcome", "ELIGIBLE")));
					identity.completeAddressFromDigiLocker(context);
				}
				case INELIGIBLE -> {
					context.failTechnically(PartnerStep.CREDIT_CHECK, "CREDIT_SCORE_BELOW_THRESHOLD");
					stateMachine.apply(context.application(), LifecycleEvent.MARK_INELIGIBLE, "SYSTEM", null);
				}
				case UNABLE_TO_ASSESS, PENDING -> context.fail(PartnerStep.CREDIT_CHECK,
						assessment.failureCode() == null ? "UNABLE_TO_ASSESS" : assessment.failureCode());
			}
			return null;
		});
		return applications.get(applicantId, publicId);
	}
}
