package in.eko.eloka.partner.shared.error;

import java.time.Duration;
import org.springframework.http.HttpStatus;

/** Too many attempts. {@code retryAfter} tells the client when to try again. */
public class RateLimitedException extends DomainException {

	private final Duration retryAfter;

	public RateLimitedException(String code, String message, Duration retryAfter) {
		super(code, message);
		this.retryAfter = retryAfter;
	}

	public Duration getRetryAfter() {
		return retryAfter;
	}

	@Override
	public HttpStatus getStatus() {
		return HttpStatus.TOO_MANY_REQUESTS;
	}
}
