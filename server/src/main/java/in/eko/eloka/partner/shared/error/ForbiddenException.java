package in.eko.eloka.partner.shared.error;

import org.springframework.http.HttpStatus;

/** The caller is known but may not do this. */
public class ForbiddenException extends DomainException {

	public ForbiddenException(String code, String message) {
		super(code, message);
	}

	@Override
	public HttpStatus getStatus() {
		return HttpStatus.FORBIDDEN;
	}
}
