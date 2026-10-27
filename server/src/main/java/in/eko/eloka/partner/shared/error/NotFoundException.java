package in.eko.eloka.partner.shared.error;

import org.springframework.http.HttpStatus;

/** The thing does not exist, or the caller is not allowed to know it exists. */
public class NotFoundException extends DomainException {

	public NotFoundException(String code, String message) {
		super(code, message);
	}

	@Override
	public HttpStatus getStatus() {
		return HttpStatus.NOT_FOUND;
	}
}
