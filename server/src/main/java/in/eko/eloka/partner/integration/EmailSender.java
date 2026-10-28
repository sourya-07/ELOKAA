package in.eko.eloka.partner.integration;

/**
 * Sends a short plain-text email (verification codes). EPS has no email OTP API, so this is our
 * own sender behind a port: SMTP in staging and production, a fake locally (ADR-0007).
 */
public interface EmailSender {

	/**
	 * @throws ProviderNotConfiguredException when no sender is configured
	 * @throws ProviderUnavailableException when the mail server refuses or cannot be reached
	 */
	void send(String to, String subject, String text);
}
