package in.eko.eloka.partner.onboarding.internal;

import java.util.UUID;
import in.eko.eloka.partner.shared.error.ApiProblem;
import in.eko.eloka.partner.shared.security.Caller;
import in.eko.eloka.partner.shared.security.CallerContext;
import in.eko.eloka.partner.shared.security.SessionKind;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/** Small helpers shared by the {@code /v1/partner/applications} controllers. */
final class WebSupport {

	private WebSupport() {
	}

	/** The applicant behind this request (security already requires an applicant session). */
	static UUID applicantId() {
		Caller caller = CallerContext.require();
		if (caller.kind() != SessionKind.PARTNER_APPLICANT) {
			throw new ApiProblem(HttpStatus.FORBIDDEN, "FORBIDDEN", "You are not allowed to do this.");
		}
		return caller.subjectId();
	}

	/** {@code If-Match: "v14"} → 14. Writes without it get {@code 428 PRECONDITION_REQUIRED}. */
	static long expectedVersion(String ifMatch) {
		if (ifMatch == null || ifMatch.isBlank()) {
			throw new ApiProblem(HttpStatus.PRECONDITION_REQUIRED, "PRECONDITION_REQUIRED",
					"Send If-Match with the ETag of the application you are changing");
		}
		String value = ifMatch.trim();
		if (value.startsWith("W/")) {
			value = value.substring(2);
		}
		value = value.replace("\"", "");
		if (value.startsWith("v")) {
			value = value.substring(1);
		}
		try {
			return Long.parseLong(value);
		}
		catch (NumberFormatException ex) {
			throw new ApiProblem(HttpStatus.PRECONDITION_FAILED, "PRECONDITION_FAILED", "If-Match is not a valid ETag");
		}
	}

	static String etag(ApplicationSnapshot snapshot) {
		return "\"v" + snapshot.version() + "\"";
	}

	static ResponseEntity<ApplicationSnapshot> ok(ApplicationSnapshot snapshot) {
		return ResponseEntity.ok().eTag(etag(snapshot)).body(snapshot);
	}

	/** Method, path and body: what makes two requests "the same" for idempotency. */
	static String fingerprint(HttpServletRequest request, Object body) {
		return request.getMethod() + " " + request.getRequestURI() + "\n" + (body == null ? "" : body.toString());
	}
}
