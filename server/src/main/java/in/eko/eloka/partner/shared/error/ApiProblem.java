package in.eko.eloka.partner.shared.error;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;

/**
 * A business failure with an explicit HTTP status and extra problem+json members, for the
 * cases the generic exceptions do not cover (412 with the current snapshot, 423 blocked,
 * 410 expired, 401 with attempts left).
 */
public class ApiProblem extends DomainException {

	private final HttpStatus status;
	private final Map<String, Object> properties;

	public ApiProblem(HttpStatus status, String code, String message) {
		this(status, code, message, Map.of());
	}

	public ApiProblem(HttpStatus status, String code, String message, Map<String, ?> properties) {
		super(code, message);
		this.status = status;
		this.properties = new LinkedHashMap<>(properties);
	}

	@Override
	public HttpStatus getStatus() {
		return status;
	}

	/** Extra members added to the problem body (e.g. {@code current}, {@code attempts_left}). */
	public Map<String, Object> getProperties() {
		return Map.copyOf(properties);
	}
}
