package in.eko.eloka.partner.integration.internal;

import in.eko.eloka.partner.integration.EmailSender;
import in.eko.eloka.partner.integration.ProviderNotConfiguredException;

/** No mail server in this environment: nothing is sent and the caller is told so. */
class UnconfiguredEmailSender implements EmailSender {

	@Override
	public void send(String to, String subject, String text) {
		throw new ProviderNotConfiguredException("Email");
	}
}
