package in.eko.eloka.partner.onboarding.internal;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import com.fasterxml.jackson.annotation.JsonProperty;
import in.eko.eloka.partner.shared.crypto.BlindIndex;
import in.eko.eloka.partner.shared.error.NotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The applicant's application. Every answer carries the snapshot and its {@code ETag}; every
 * change needs {@code If-Match}. Another applicant's application id answers 404.
 */
@RestController
@RequestMapping("/v1/partner/applications")
class ApplicationController {

	private final ApplicationService applications;
	private final Idempotency idempotency;
	private final BlindIndex index;

	ApplicationController(ApplicationService applications, Idempotency idempotency, BlindIndex index) {
		this.applications = applications;
		this.idempotency = idempotency;
		this.index = index;
	}

	record ConsentRequest(String purpose, String version) {
	}

	record MethodRequest(String method) {
	}

	record DeclarationRequest(String version, boolean accepted) {
	}

	record TimelineItem(@JsonProperty("event_type") String eventType, @JsonProperty("step_key") String stepKey,
			@JsonProperty("to_status") String toStatus, @JsonProperty("occurred_at") Instant occurredAt) {
	}

	/** Continue the open application, or start one: 201 when new, 200 when resumed. */
	@PostMapping
	ResponseEntity<ApplicationSnapshot> start() {
		ApplicationService.Start start = applications.startOrResume(WebSupport.applicantId());
		return ResponseEntity.status(start.resumed() ? HttpStatus.OK : HttpStatus.CREATED)
			.eTag(WebSupport.etag(start.snapshot()))
			.body(start.snapshot());
	}

	@GetMapping("/current")
	ResponseEntity<ApplicationSnapshot> current() {
		return applications.current(WebSupport.applicantId())
			.map(WebSupport::ok)
			.orElseThrow(() -> new NotFoundException("NO_APPLICATION", "You have not started an application yet"));
	}

	@GetMapping("/{id}")
	ResponseEntity<ApplicationSnapshot> get(@PathVariable String id) {
		return WebSupport.ok(applications.get(WebSupport.applicantId(), id));
	}

	@GetMapping("/{id}/timeline")
	List<TimelineItem> timeline(@PathVariable String id) {
		return applications.timeline(WebSupport.applicantId(), id)
			.stream()
			.map(entry -> new TimelineItem(entry.getEventType(),
					entry.getStepKey() == null ? null : entry.getStepKey().name(),
					entry.getToStatus() == null ? null : entry.getToStatus().name(), entry.getOccurredAt()))
			.toList();
	}

	@PostMapping("/{id}/consents")
	ResponseEntity<ApplicationSnapshot> consent(@PathVariable String id, @RequestBody ConsentRequest body,
			@RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch, HttpServletRequest request) {
		long version = WebSupport.expectedVersion(ifMatch);
		return WebSupport.ok(applications.consent(WebSupport.applicantId(), id, version, body.purpose(), body.version(),
				index.hash("ip", request.getRemoteAddr())));
	}

	@PutMapping("/{id}/method")
	ResponseEntity<ApplicationSnapshot> method(@PathVariable String id, @RequestBody MethodRequest body,
			@RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch) {
		long version = WebSupport.expectedVersion(ifMatch);
		return WebSupport.ok(applications.chooseMethod(WebSupport.applicantId(), id, version, body.method()));
	}

	@PostMapping("/{id}/declaration")
	ResponseEntity<ApplicationSnapshot> declaration(@PathVariable String id, @RequestBody DeclarationRequest body,
			@RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch) {
		long version = WebSupport.expectedVersion(ifMatch);
		return WebSupport.ok(applications.declare(WebSupport.applicantId(), id, version, body.version(), body.accepted()));
	}

	/** Needs {@code If-Match} and {@code Idempotency-Key}: a double click submits once. */
	@PostMapping("/{id}/submit")
	ResponseEntity<?> submit(@PathVariable String id,
			@RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch,
			@RequestHeader(value = Idempotency.HEADER, required = false) String key, HttpServletRequest request) {
		long version = WebSupport.expectedVersion(ifMatch);
		UUID applicantId = WebSupport.applicantId();
		return idempotency.run(applicantId, key, WebSupport.fingerprint(request, version),
				() -> WebSupport.ok(applications.submit(applicantId, id, version)));
	}
}
