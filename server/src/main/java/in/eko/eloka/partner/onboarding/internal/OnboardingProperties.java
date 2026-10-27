package in.eko.eloka.partner.onboarding.internal;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Application rules under {@code eloka.onboarding}. Open business questions
 * (docs/OPEN_QUESTIONS.md) live here as settings, not in code.
 *
 * @param idleExpiry an application in progress expires after this long without applicant activity
 * @param maxAttempts failed verification attempts per step before it is blocked
 * @param consentVersion current version of the data-use consent text the client shows
 * @param declarationVersion current version of the final declaration text
 * @param digilockerReturnUrl client page DigiLocker sends the applicant back to; {@code {state}} is replaced
 */
@ConfigurationProperties("eloka.onboarding")
record OnboardingProperties(Duration idleExpiry, int maxAttempts, String consentVersion, String declarationVersion,
		String digilockerReturnUrl) {

	OnboardingProperties {
		if (digilockerReturnUrl == null || !digilockerReturnUrl.contains("{state}")) {
			throw new IllegalStateException("eloka.onboarding.digilocker-return-url must contain {state}");
		}
		idleExpiry = idleExpiry == null ? Duration.ofDays(30) : idleExpiry;
		maxAttempts = maxAttempts == 0 ? 3 : maxAttempts;
		consentVersion = consentVersion == null ? "2026-10-27" : consentVersion;
		declarationVersion = declarationVersion == null ? "2026-10-27" : declarationVersion;
	}
}
