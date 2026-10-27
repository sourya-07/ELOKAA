package in.eko.eloka.partner.identity;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Staff sign-in under {@code eloka.staff}.
 *
 * @param googleClientId OAuth client id of the staff console; empty refuses every Google sign-in
 * @param googleDomain the only Google Workspace domain whose accounts may sign in (e.g. eko.co.in)
 * @param otpLoginEnabled local/test only: staff sign in with a mobile OTP (refused in staging and production)
 */
@ConfigurationProperties("eloka.staff")
public record StaffProperties(String googleClientId, String googleDomain, boolean otpLoginEnabled) {
}
