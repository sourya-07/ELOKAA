package in.eko.eloka.partner.integration;

import in.eko.eloka.partner.shared.error.ConflictException;

/** No provider is configured for this check in this environment. Never treated as a pass. */
public class ProviderNotConfiguredException extends ConflictException {

	public ProviderNotConfiguredException(String provider) {
		super("PROVIDER_NOT_CONFIGURED", provider + " is not available yet. Please try again later.");
	}
}
