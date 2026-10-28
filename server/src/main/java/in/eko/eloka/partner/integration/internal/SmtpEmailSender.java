package in.eko.eloka.partner.integration.internal;

import in.eko.eloka.partner.integration.EmailSender;
import in.eko.eloka.partner.integration.ProviderUnavailableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

/**
 * Plain-text mail through the SMTP server in {@code spring.mail.*}. Failures are logged without the
 * address or the text (the text holds a code).
 */
class SmtpEmailSender implements EmailSender {

	private static final Logger log = LoggerFactory.getLogger(SmtpEmailSender.class);

	private final JavaMailSender mail;
	private final String from;

	SmtpEmailSender(JavaMailSender mail, String from) {
		if (from == null || from.isBlank()) {
			throw new IllegalStateException("eloka.integration.email-from is required for the SMTP email sender");
		}
		this.mail = mail;
		this.from = from;
	}

	@Override
	public void send(String to, String subject, String text) {
		SimpleMailMessage message = new SimpleMailMessage();
		message.setFrom(from);
		message.setTo(to);
		message.setSubject(subject);
		message.setText(text);
		try {
			mail.send(message);
		}
		catch (MailException ex) {
			log.warn("Email not sent: {}", ex.getClass().getSimpleName());
			throw new ProviderUnavailableException("Email");
		}
	}
}
