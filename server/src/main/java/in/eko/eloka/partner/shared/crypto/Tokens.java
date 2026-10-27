package in.eko.eloka.partner.shared.crypto;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

/** Random secrets and their stored hashes (session tokens, DigiLocker state). */
public final class Tokens {

	private static final SecureRandom RANDOM = new SecureRandom();

	private Tokens() {
	}

	/** @return {@code prefix} + 256 random bits, base64url without padding */
	public static String random(String prefix) {
		byte[] bytes = new byte[32];
		RANDOM.nextBytes(bytes);
		return prefix + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
	}

	/** SHA-256 of the token: what the database stores instead of the token itself. */
	public static byte[] sha256(String token) {
		try {
			return MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
		}
		catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException(ex);
		}
	}
}
