package in.eko.eloka.partner.shared.error;

import org.springframework.http.HttpStatus;

/**
 * Base class for every expected business failure. Each module extends it with its own
 * exception (for example {@code ApplicationNotFoundException}) so the HTTP mapping lives
 * in one place: {@link in.eko.eloka.partner.shared.web.ApiExceptionHandler}.
 */
public abstract class DomainException extends RuntimeException {

	private final String code;

	protected DomainException(String code, String message) {
		super(message);
		this.code = code;
	}

	/** Stable, machine-readable error code returned to clients, e.g. {@code APPLICATION_NOT_FOUND}. */
	public String getCode() {
		return code;
	}

	/** HTTP status this failure maps to. */
	public abstract HttpStatus getStatus();
}
