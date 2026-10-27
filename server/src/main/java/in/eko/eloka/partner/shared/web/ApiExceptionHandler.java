package in.eko.eloka.partner.shared.web;

import in.eko.eloka.partner.shared.error.ApiProblem;
import in.eko.eloka.partner.shared.error.DomainException;
import in.eko.eloka.partner.shared.error.RateLimitedException;
import in.eko.eloka.partner.shared.error.ValidationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
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

	@ExceptionHandler(Exception.class)
	ProblemDetail handleUnexpected(Exception ex) {
		log.error("Unexpected error", ex);
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR,
				"Something went wrong on our side. Please try again.");
		problem.setProperty("code", "INTERNAL_ERROR");
		return problem;
	}
}
