package in.eko.eloka.partner.onboarding.internal;

import java.time.Instant;
import java.util.List;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import tools.jackson.databind.JsonNode;

/**
 * What the applicant sees after every call. The client renders from this and never infers
 * progress on its own; {@code next_step} is the server's answer to "where do I continue?".
 */
record ApplicationSnapshot(@JsonProperty("application_id") String applicationId, String status, long version,
		String method, @JsonProperty("next_step") String nextStep, @JsonProperty("expires_at") Instant expiresAt,
		@JsonProperty("submitted_at") Instant submittedAt, List<Step> steps,
		@JsonProperty("available_actions") List<String> availableActions) {

	@JsonInclude(JsonInclude.Include.NON_NULL)
	record Step(@JsonProperty("step_key") String stepKey, String status,
			@JsonProperty("attempt_count") int attemptCount, @JsonProperty("max_attempts") int maxAttempts,
			@JsonProperty("last_error_code") String lastErrorCode, JsonNode summary,
			@JsonProperty("reopen_reason_code") String reopenReasonCode,
			@JsonProperty("reviewer_message") String reviewerMessage) {
	}
}
