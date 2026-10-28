package in.eko.eloka.partner.identity;

import java.util.UUID;

/**
 * Proves the applicant controls an email address: a 6-digit code by email, with the same kind of
 * limits as mobile codes (expiry, wrong guesses, single use, sends per address, IP and application).
 * Unlike mobile OTP the code is ours: only a keyed hash of it is stored.
 */
public interface EmailVerification {

	/** @param challengeRef what the client sends back with the code */
	record Sent(String challengeRef, long expiresInSeconds, long resendAfterSeconds, int codeLength,
			String emailMasked) {
	}

	/** @param email normalised (trimmed, lower case); @param emailHash blind index for uniqueness */
	record Verified(String email, byte[] emailHash, String emailMasked) {
	}

	/**
	 * @return the address trimmed and in lower case
	 * @throws in.eko.eloka.partner.shared.error.ValidationException when it is not an email address
	 */
	String normalise(String email);

	/** Blind index of a normalised address, for "is it used elsewhere" checks before sending. */
	byte[] hash(String normalisedEmail);

	/**
	 * Sends a code. {@code subjectId} is what the code belongs to (the application), so a code
	 * cannot be used on another one.
	 *
	 * @throws in.eko.eloka.partner.shared.error.RateLimitedException {@code OTP_LIMIT_REACHED}
	 */
	Sent send(UUID subjectId, String email, String clientIp);

	/**
	 * @throws in.eko.eloka.partner.shared.error.ApiProblem {@code OTP_INVALID} (with attempts left),
	 *         {@code OTP_LOCKED} or {@code OTP_EXPIRED}
	 */
	Verified verify(UUID subjectId, String challengeRef, String code);
}
