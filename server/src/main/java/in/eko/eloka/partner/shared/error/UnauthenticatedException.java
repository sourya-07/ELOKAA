package in.eko.eloka.partner.shared.error;

import org.springframework.http.HttpStatus;

/** No valid session. */
public class UnauthenticatedException extends DomainException {

	public UnauthenticatedException(String message) {
		super("UNAUTHENTICATED", message);
	}

	@Override
	public HttpStatus getStatus() {
		return HttpStatus.UNAUTHORIZED;
	}
}
