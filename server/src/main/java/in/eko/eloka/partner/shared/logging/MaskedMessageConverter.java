package in.eko.eloka.partner.shared.logging;

import ch.qos.logback.classic.pattern.MessageConverter;
import ch.qos.logback.classic.spi.ILoggingEvent;

/** Logback {@code %maskedMsg}: the log message with personal data redacted. */
public class MaskedMessageConverter extends MessageConverter {

	@Override
	public String convert(ILoggingEvent event) {
		return PiiMasker.mask(super.convert(event));
	}
}
