package in.eko.eloka.partner.shared.error;

import org.springframework.http.HttpStatus;

/** The request is valid but clashes with the current state (duplicate, wrong state). */
public class ConflictException extends DomainException {

	public ConflictException(String code, String message) {
		super(code, message);
	}

	@Override
	public HttpStatus getStatus() {
		return HttpStatus.CONFLICT;
	}
}
