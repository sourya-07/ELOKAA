package in.eko.eloka.partner.shared.web;

import in.eko.eloka.partner.shared.error.ApiProblem;
import in.eko.eloka.partner.shared.error.DomainException;
import in.eko.eloka.partner.shared.error.RateLimitedException;
import in.eko.eloka.partner.shared.error.ValidationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Turns exceptions into RFC 9457 {@code application/problem+json} responses.
 * Framework errors (validation, 404, 405) are handled by the parent class. Anything
 * unexpected is logged once with the request id and answered with a generic 500: no stack
 * trace or internal message ever reaches the client.
 */
@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

	@ExceptionHandler(DomainException.class)
	ProblemDetail handleDomainException(DomainException ex) {
		if (ex.getStatus().is5xxServerError()) {
			log.error("{}: {}", ex.getCode(), ex.getMessage());
		}
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(ex.getStatus(), ex.getMessage());
		problem.setProperty("code", ex.getCode());
		if (ex instanceof ValidationException validation && !validation.getInvalidParams().isEmpty()) {
			problem.setProperty("invalid_params", validation.getInvalidParams());
		}
		if (ex instanceof ApiProblem apiProblem) {
			apiProblem.getProperties().forEach(problem::setProperty);
		}
		if (ex instanceof RateLimitedException limited && limited.getRetryAfter() != null) {
			problem.setProperty("retry_after", limited.getRetryAfter().toSeconds());
		}
		return problem;
	}

	/** A unique key won a race (the same PAN or mobile in two places at once): the loser gets a clean 409. */
	@ExceptionHandler(DataIntegrityViolationException.class)
	ProblemDetail handleConflict(DataIntegrityViolationException ex) {
		log.warn("Unique constraint refused a write: {}", ex.getMostSpecificCause().getClass().getSimpleName());
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,
				"This was changed at the same moment somewhere else. Please reload and try again.");
		problem.setProperty("code", "CONFLICT");
		return problem;
	}

	/** Uploads above the server's size limit (the parent class maps them to 413). */
	@Override
	protected ResponseEntity<Object> handleMaxUploadSizeExceededException(MaxUploadSizeExceededException ex,
			HttpHeaders headers, HttpStatusCode status, WebRequest request) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONTENT_TOO_LARGE,
				"The file is too large. Documents can be at most 5 MB and photos 2 MB.");
		problem.setProperty("code", "FILE_TOO_LARGE");
		return ResponseEntity.status(HttpStatus.CONTENT_TOO_LARGE).body(problem);
	}

	/**
	 * Framework refusals (unreadable body, wrong value type, 404, 405, 415) get a {@code code} like every
	 * other error, so the client can always map one (docs/ERROR_CATALOG.md).
	 */
	@Override
	protected ResponseEntity<Object> handleExceptionInternal(Exception ex, Object body, HttpHeaders headers,
			HttpStatusCode statusCode, WebRequest request) {
		// The parent builds the problem body when none is given, so the code is added to what it returns
		ResponseEntity<Object> response = super.handleExceptionInternal(ex, body, headers, statusCode, request);
		if (response != null && response.getBody() instanceof ProblemDetail problem
				&& (problem.getProperties() == null || !problem.getProperties().containsKey("code"))) {
			problem.setProperty("code", frameworkCode(statusCode.value()));
		}
		return response;
	}

	static String frameworkCode(int status) {
		return switch (status) {
			case 400 -> "MALFORMED_REQUEST";
			case 404 -> "NOT_FOUND";
			case 405 -> "METHOD_NOT_ALLOWED";
			case 406 -> "NOT_ACCEPTABLE";
			case 415 -> "UNSUPPORTED_MEDIA_TYPE";
			case 413 -> "FILE_TOO_LARGE";
			default -> status >= 500 ? "INTERNAL_ERROR" : "REQUEST_REJECTED";
		};
	}

	@ExceptionHandler(Exception.class)
	ProblemDetail handleUnexpected(Exception ex) {
		log.error("Unexpected error", ex);
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR,
				"Something went wrong on our side. Please try again.");
		problem.setProperty("code", "INTERNAL_ERROR");
		return problem;
	}
}
