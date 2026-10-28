package in.eko.eloka.partner.identity;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Sign-in and session rules under {@code eloka.identity}.
 *
 * @param otpLength digits in an OTP (shown to the client for the input boxes)
 * @param otpTtl how long an OTP is valid on our side (the provider's expiry is used if earlier)
 * @param otpMaxAttempts wrong guesses allowed per OTP
 * @param otpMinGap minimum time between two OTPs to the same mobile
 * @param otpMaxPerHour OTPs per mobile per hour
 * @param otpMaxPerDay OTPs per mobile per day
 * @param otpMaxPerIpPerHour OTPs per client IP per hour
 * @param emailOtpTtl how long an email code is valid (email can arrive slower than SMS); the other
 *        OTP limits apply to email codes too, counted per address
 * @param sessionIdleTimeout session ends after this much inactivity
 * @param accessTokenTtl an access token works this long; then it is rotated with the refresh token
 * @param sessionAbsoluteTtl session ends this long after sign-in, whatever happens
 */
@ConfigurationProperties("eloka.identity")
public record IdentityProperties(int otpLength, Duration otpTtl, int otpMaxAttempts, Duration otpMinGap,
		int otpMaxPerHour, int otpMaxPerDay, int otpMaxPerIpPerHour, Duration emailOtpTtl, Duration sessionIdleTimeout,
		Duration sessionAbsoluteTtl, Duration accessTokenTtl) {

	public IdentityProperties {
		otpLength = otpLength == 0 ? 6 : otpLength;
		otpTtl = otpTtl == null ? Duration.ofMinutes(5) : otpTtl;
		otpMaxAttempts = otpMaxAttempts == 0 ? 5 : otpMaxAttempts;
		otpMinGap = otpMinGap == null ? Duration.ofSeconds(30) : otpMinGap;
		otpMaxPerHour = otpMaxPerHour == 0 ? 5 : otpMaxPerHour;
		otpMaxPerDay = otpMaxPerDay == 0 ? 10 : otpMaxPerDay;
		otpMaxPerIpPerHour = otpMaxPerIpPerHour == 0 ? 10 : otpMaxPerIpPerHour;
		emailOtpTtl = emailOtpTtl == null ? Duration.ofMinutes(10) : emailOtpTtl;
		sessionIdleTimeout = sessionIdleTimeout == null ? Duration.ofHours(2) : sessionIdleTimeout;
		sessionAbsoluteTtl = sessionAbsoluteTtl == null ? Duration.ofHours(12) : sessionAbsoluteTtl;
		accessTokenTtl = accessTokenTtl == null ? Duration.ofMinutes(30) : accessTokenTtl;
	}
}
