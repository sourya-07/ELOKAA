package in.eko.eloka.partner.onboarding.internal;

import java.util.Map;
import java.util.UUID;
import com.fasterxml.jackson.annotation.JsonProperty;
import in.eko.eloka.partner.audit.AuditEntry;
import in.eko.eloka.partner.audit.AuditLog;
import in.eko.eloka.partner.identity.EmailVerification;
import in.eko.eloka.partner.onboarding.PartnerStep;
import in.eko.eloka.partner.onboarding.StepStatus;
import in.eko.eloka.partner.shared.crypto.FieldCipher;
import in.eko.eloka.partner.shared.error.ApiProblem;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

/**
 * The email part of "contact details": send a code to an address, then prove it. One email per
 * open, approved or ineligible application, like the PAN (docs/OPEN_QUESTIONS.md Q-25); the
 * refusal does not say which application has it.
 */
@Service
class ContactSteps {

	record EmailCodeSent(@JsonProperty("challenge_ref") String challengeRef, @JsonProperty("expires_in") long expiresIn,
			@JsonProperty("resend_after") long resendAfter, @JsonProperty("otp_length") int otpLength,
			@JsonProperty("email_masked") String emailMasked) {
	}

	private final ApplicationService applications;
	private final ApplicationStepRepository steps;
	private final PartnerApplicationRepository applicationRows;
	private final EmailVerification emails;
	private final FieldCipher cipher;
	private final AuditLog audit;
	private final JsonMapper json;

	ContactSteps(ApplicationService applications, ApplicationStepRepository steps,
			PartnerApplicationRepository applicationRows, EmailVerification emails, FieldCipher cipher, AuditLog audit,
			JsonMapper json) {
		this.applications = applications;
		this.steps = steps;
		this.applicationRows = applicationRows;
		this.emails = emails;
		this.cipher = cipher;
		this.audit = audit;
		this.json = json;
	}

	/**
	 * Sends a code. It changes nothing on the application (no version change, so the applicant's
	 * {@code If-Match} stays valid for the verification that follows).
	 */
	EmailCodeSent sendEmailCode(UUID applicantId, String publicId, String email, String clientIp) {
		PartnerApplication application = applications.find(applicantId, publicId);
		if (!application.getStatus().isEditableByApplicant()) {
			throw new ApiProblem(HttpStatus.CONFLICT, "APPLICATION_NOT_EDITABLE", "The application can no longer be changed",
					Map.of("current_status", application.getStatus().name()));
		}
		steps.findByApplicationId(application.getId())
			.stream()
			.filter(step -> step.getStepKey() == PartnerStep.EMAIL_VERIFICATION && step.getStatus() == StepStatus.LOCKED)
			.findAny()
			.ifPresent(step -> {
				throw new ApiProblem(HttpStatus.CONFLICT, "STEP_NOT_EDITABLE", "Your email cannot be changed now");
			});
		String address = emails.normalise(email);
		requireFree(emails.hash(address), application.getId());
		EmailVerification.Sent sent = emails.send(application.getId(), address, clientIp);
		return new EmailCodeSent(sent.challengeRef(), sent.expiresInSeconds(), sent.resendAfterSeconds(), sent.codeLength(),
				sent.emailMasked());
	}

	/** Checks the code and keeps the address; a new address makes the declaration stale. */
	ApplicationSnapshot verifyEmail(UUID applicantId, String publicId, long version, String challengeRef, String code) {
		try {
			applications.write(applicantId, publicId, version, context -> {
				context.requirePrerequisites(PartnerStep.EMAIL_VERIFICATION);
				EmailVerification.Verified verified = emails.verify(context.applicationId(), challengeRef, code);
				requireFree(verified.emailHash(), context.applicationId());
				context.application().bindEmail(cipher.encrypt(verified.email()), verified.emailHash(), verified.emailMasked());
				context.complete(PartnerStep.EMAIL_VERIFICATION,
						json.writeValueAsString(Map.of("email_masked", verified.emailMasked())), verified.emailHash());
				audit.record(new AuditEntry("EMAIL_VERIFIED", "APPLICANT", applicantId.toString(), "PARTNER_APPLICATION",
						publicId, Map.of()));
				return null;
			});
		}
		catch (DataIntegrityViolationException ex) {
			// another application took the address between our check and the save
			throw emailUsed();
		}
		return applications.get(applicantId, publicId);
	}

	private void requireFree(byte[] emailHash, UUID applicationId) {
		if (applicationRows.existsBlockingEmail(emailHash, applicationId, applications.blockingStatuses())) {
			throw emailUsed();
		}
	}

	private static ApiProblem emailUsed() {
		return new ApiProblem(HttpStatus.CONFLICT, "EMAIL_ALREADY_USED", "Please use a different email address.");
	}
}
