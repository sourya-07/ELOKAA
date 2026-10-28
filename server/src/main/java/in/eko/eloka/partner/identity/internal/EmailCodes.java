package in.eko.eloka.partner.identity.internal;

import java.security.SecureRandom;
import in.eko.eloka.partner.shared.config.UnsafeInProduction;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Makes email codes: random, or one fixed code for local work and tests (refused in production). */
interface EmailCodes {

	String next(int length);

	/** {@code eloka.identity.email-fixed-code} set (local/test): that code every time. */
	@Configuration(proxyBeanMethods = false)
	class Config {

		@Bean
		EmailCodes emailCodes(@Value("${eloka.identity.email-fixed-code:}") String fixed) {
			return fixed.isBlank() ? new Random() : new Fixed(fixed);
		}
	}

	final class Random implements EmailCodes {

		private final SecureRandom random = new SecureRandom();

		@Override
		public String next(int length) {
			StringBuilder code = new StringBuilder(length);
			for (int i = 0; i < length; i++) {
				code.append(random.nextInt(10));
			}
			return code.toString();
		}
	}

	record Fixed(String code) implements EmailCodes, UnsafeInProduction {

		@Override
		public String next(int length) {
			return code;
		}

		@Override
		public String unsafeReason() {
			return "a fixed email code is active (eloka.identity.email-fixed-code)";
		}
	}
}
