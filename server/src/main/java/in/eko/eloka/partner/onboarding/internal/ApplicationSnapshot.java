package in.eko.eloka.partner.onboarding.internal;

import java.time.Instant;
import java.util.List;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import tools.jackson.databind.JsonNode;

/**
 * What the applicant sees after every call. The client renders from this and never infers
 * progress on its own; {@code next_step} is the server's answer to "where do I continue?".
 * {@code application_id} identifies the application in the API; {@code application_number} is the
 * 10-digit number people read and quote (display only, never accepted as a lookup).
 */
record ApplicationSnapshot(@JsonProperty("application_id") String applicationId,
		@JsonProperty("application_number") String applicationNumber, Contact contact, String status, long version,
		String method, @JsonProperty("next_step") String nextStep, @JsonProperty("expires_at") Instant expiresAt,
		@JsonProperty("submitted_at") Instant submittedAt, List<Step> steps,
		@JsonProperty("available_actions") List<String> availableActions,
		List<DocumentRequirements.Status> requirements,
		@JsonProperty("submit_blockers") List<Blocker> submitBlockers) {

	/** The verified mobile and email, masked by the server; the full values never leave it. */
	record Contact(@JsonProperty("mobile_masked") String mobileMasked, @JsonProperty("email_masked") String emailMasked) {
	}

	/**
	 * Why the applicant cannot submit yet: a step that is not done ({@code code} says why), or a
	 * file rule not met ({@code group} names the checklist line). Empty when submit is possible.
	 */
	@JsonInclude(JsonInclude.Include.NON_NULL)
	record Blocker(@JsonProperty("step_key") String stepKey, String code, String group) {
	}

	@JsonInclude(JsonInclude.Include.NON_NULL)
	record Step(@JsonProperty("step_key") String stepKey, String status,
			@JsonProperty("attempt_count") int attemptCount, @JsonProperty("max_attempts") int maxAttempts,
			@JsonProperty("last_error_code") String lastErrorCode, JsonNode summary,
			@JsonProperty("reopen_reason_code") String reopenReasonCode,
			@JsonProperty("reviewer_message") String reviewerMessage) {
	}
}
