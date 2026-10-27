package in.eko.eloka.partner.integration;

import java.time.Instant;

/**
 * Sends a one-time password by SMS and checks the code the person typed. The provider owns the
 * code; we never see or store it. Expiry, attempt limits, rate limits and single use are
 * enforced by the identity module on top of this port.
 */
public interface MobileOtpGateway {

	/**
	 * @param mobile normalised 10-digit mobile
	 * @return the provider's reference for this OTP, and its expiry if the provider gives one
	 * @throws ProviderNotConfiguredException when no provider is configured
	 * @throws ProviderUnavailableException when the provider cannot be reached or answers badly
	 */
	SentOtp send(String mobile);

	/**
	 * @return true only when the provider confirms the code for this mobile and reference;
	 *     false for a wrong or expired code
	 * @throws ProviderUnavailableException when the provider cannot be reached or answers badly
	 */
	boolean verify(String mobile, String reference, String code);

	/** @param expiresAt provider's expiry, or {@code null} when it gives none */
	record SentOtp(String reference, Instant expiresAt) {
	}
}
