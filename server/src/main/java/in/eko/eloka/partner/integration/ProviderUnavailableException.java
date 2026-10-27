package in.eko.eloka.partner.integration;

import in.eko.eloka.partner.shared.error.DomainException;
import org.springframework.http.HttpStatus;

/** The provider timed out, failed or sent an answer we could not trust. Never treated as a pass. */
public class ProviderUnavailableException extends DomainException {

	public ProviderUnavailableException(String provider) {
		super("PROVIDER_UNAVAILABLE", provider + " is not responding. Please try again in a few minutes.");
	}

	@Override
	public HttpStatus getStatus() {
		return HttpStatus.SERVICE_UNAVAILABLE;
	}
}
