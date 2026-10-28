package in.eko.eloka.partner.onboarding.internal;

import java.time.Duration;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Application rules under {@code eloka.onboarding}. Open business questions
 * (docs/OPEN_QUESTIONS.md) live here as settings, not in code.
 *
 * @param idleExpiry an application in progress expires after this long without applicant activity
 * @param maxAttempts failed verification attempts per step before it is blocked
 * @param consentVersion current version of the consent texts the client shows
 * @param consentVersions per-purpose versions that override {@code consentVersion}, so a new data-use
 *        text does not void DigiLocker or credit-check consents already given
 * @param declarationVersion current version of the final declaration text
 * @param digilockerReturnUrl client page DigiLocker sends the applicant back to; {@code {state}} is replaced
 */
@ConfigurationProperties("eloka.onboarding")
record OnboardingProperties(Duration idleExpiry, int maxAttempts, String consentVersion,
		Map<ConsentRecord.Purpose, String> consentVersions, String declarationVersion, String digilockerReturnUrl) {

	OnboardingProperties {
		if (digilockerReturnUrl == null || !digilockerReturnUrl.contains("{state}")) {
			throw new IllegalStateException("eloka.onboarding.digilocker-return-url must contain {state}");
		}
		idleExpiry = idleExpiry == null ? Duration.ofDays(30) : idleExpiry;
		maxAttempts = maxAttempts == 0 ? 3 : maxAttempts;
		consentVersion = consentVersion == null ? "2026-10-27" : consentVersion;
		consentVersions = consentVersions == null ? Map.of() : Map.copyOf(consentVersions);
		declarationVersion = declarationVersion == null ? "2026-10-27" : declarationVersion;
	}

	/** The text version an applicant must have agreed to for {@code purpose}. */
	String consentVersion(ConsentRecord.Purpose purpose) {
		return consentVersions.getOrDefault(purpose, consentVersion);
	}
}
