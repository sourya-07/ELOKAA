package in.eko.eloka.partner.shared.error;

import java.util.Map;
import org.springframework.http.HttpStatus;

/** Input is missing or malformed. {@code invalidParams} maps field name → reason. */
public class ValidationException extends DomainException {

	private final Map<String, String> invalidParams;

	public ValidationException(String message, Map<String, String> invalidParams) {
		super("VALIDATION_FAILED", message);
		this.invalidParams = Map.copyOf(invalidParams);
	}

	public static ValidationException field(String field, String reason) {
		return new ValidationException(reason, Map.of(field, reason));
	}

	public Map<String, String> getInvalidParams() {
		return invalidParams;
	}

	@Override
	public HttpStatus getStatus() {
		return HttpStatus.UNPROCESSABLE_CONTENT;
	}
}
