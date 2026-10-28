package in.eko.eloka.partner.onboarding.internal;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import com.fasterxml.jackson.annotation.JsonProperty;
import in.eko.eloka.partner.audit.AuditTrail;
import in.eko.eloka.partner.documents.DocumentContent;
import in.eko.eloka.partner.shared.error.ForbiddenException;
import in.eko.eloka.partner.shared.security.Caller;
import in.eko.eloka.partner.shared.security.CallerContext;
import in.eko.eloka.partner.shared.security.SessionKind;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** The staff review console's API. Roles are checked per action in {@link ReviewService}. */
@RestController
@RequestMapping("/v1/admin/applications")
class StaffReviewController {

	private final ReviewService review;

	StaffReviewController(ReviewService review) {
		this.review = review;
	}

	record Decision(boolean verified, @JsonProperty("reason_code") String reasonCode) {
	}

	record CorrectionRequest(List<String> steps, @JsonProperty("reason_code") String reasonCode, String message,
			@JsonProperty("due_in_days") Integer dueInDays) {
	}

	record Recommendation(String decision, String note) {
	}

	record Outcome(@JsonProperty("reason_code") String reasonCode, String note) {
	}

	record Reveal(String field, String reason) {
	}

	@GetMapping
	List<ReviewService.QueueItem> queue(@RequestParam(required = false) String status,
			@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "50") int size) {
		return review.queue(staff(), status, page, size);
	}

	@GetMapping("/{id}")
	ReviewService.CaseView caseView(@PathVariable String id) {
		return review.caseView(staff(), id);
	}

	@PostMapping("/{id}/claim")
	ReviewService.CaseView claim(@PathVariable String id) {
		return review.claim(staff(), id);
	}

	@PostMapping("/{id}/release")
	ReviewService.CaseView release(@PathVariable String id) {
		return review.release(staff(), id);
	}

	@PostMapping("/{id}/documents/{documentId}/decision")
	ReviewService.CaseView documentDecision(@PathVariable String id, @PathVariable UUID documentId,
			@RequestBody Decision body) {
		return review.decideDocument(staff(), id, documentId, body.verified(), body.reasonCode());
	}

	@GetMapping("/{id}/documents/{documentId}/content")
	ResponseEntity<byte[]> documentContent(@PathVariable String id, @PathVariable UUID documentId) {
		DocumentContent content = review.documentContent(staff(), id, documentId);
		return ResponseEntity.ok()
			.contentType(MediaType.parseMediaType(content.ref().contentType()))
			.cacheControl(CacheControl.noStore())
			.header(HttpHeaders.CONTENT_DISPOSITION,
					ContentDisposition.inline().filename(content.ref().fileName()).build().toString())
			.header("X-Content-Type-Options", "nosniff")
			.body(content.bytes());
	}

	@PostMapping("/{id}/verifications/{evidenceId}/decision")
	ReviewService.CaseView verificationDecision(@PathVariable String id, @PathVariable UUID evidenceId,
			@RequestBody Decision body) {
		return review.decideVerification(staff(), id, evidenceId, body.verified(), body.reasonCode());
	}

	@PostMapping("/{id}/address/decision")
	ReviewService.CaseView addressDecision(@PathVariable String id, @RequestBody Decision body) {
		return review.decideAddress(staff(), id, body.verified(), body.reasonCode());
	}

	record Unblock(@JsonProperty("reason_code") String reasonCode, String note) {
	}

	@PostMapping("/{id}/steps/{step}/unblock")
	ReviewService.CaseView unblock(@PathVariable String id, @PathVariable String step,
			@RequestBody(required = false) Unblock body) {
		return review.unblock(staff(), id, step, body == null ? null : body.reasonCode(), body == null ? null : body.note());
	}

	@PostMapping("/{id}/corrections")
	ReviewService.CaseView corrections(@PathVariable String id, @RequestBody CorrectionRequest body) {
		return review.requestCorrection(staff(), id, body.steps(), body.reasonCode(), body.message(), body.dueInDays());
	}

	@PostMapping("/{id}/recommendation")
	ReviewService.CaseView recommend(@PathVariable String id, @RequestBody Recommendation body) {
		return review.recommend(staff(), id, body.decision(), body.note());
	}

	@PostMapping("/{id}/approve")
	ReviewService.CaseView approve(@PathVariable String id, @RequestBody(required = false) Outcome body) {
		return review.approve(staff(), id, body == null ? null : body.note());
	}

	@PostMapping("/{id}/reject")
	ReviewService.CaseView reject(@PathVariable String id, @RequestBody Outcome body) {
		return review.reject(staff(), id, body.reasonCode(), body.note());
	}

	@PostMapping("/{id}/holds")
	ReviewService.CaseView hold(@PathVariable String id, @RequestBody Outcome body) {
		return review.hold(staff(), id, body.reasonCode());
	}

	@DeleteMapping("/{id}/holds")
	ReviewService.CaseView releaseHold(@PathVariable String id) {
		return review.releaseHold(staff(), id);
	}

	@PostMapping("/{id}/pii-reveal")
	Map<String, String> reveal(@PathVariable String id, @RequestBody Reveal body) {
		return review.revealPii(staff(), id, body.field(), body.reason());
	}

	@GetMapping("/{id}/audit")
	List<AuditTrail.AuditRecord> audit(@PathVariable String id) {
		return review.auditTrail(staff(), id);
	}

	private static Caller staff() {
		Caller caller = CallerContext.require();
		if (caller.kind() != SessionKind.STAFF) {
			throw new ForbiddenException("FORBIDDEN", "You are not allowed to do this.");
		}
		return caller;
	}
}
