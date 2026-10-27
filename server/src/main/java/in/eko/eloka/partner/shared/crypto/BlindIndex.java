package in.eko.eloka.partner.shared.crypto;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Keyed hash (HMAC-SHA256) of a normalised value, so encrypted columns can still be
 * searched and made unique ("find user by mobile") without storing the value in clear.
 */
public final class BlindIndex {

	private final SecretKeySpec key;

	public BlindIndex(String base64Key) {
		this.key = new SecretKeySpec(FieldCipher.KeyMaterial.decode32(base64Key, "eloka.crypto.index-key"), "HmacSHA256");
	}

	/**
	 * @param purpose namespace such as {@code "mobile"} so equal values of different kinds hash differently
	 * @param value already-normalised value
	 * @return 32-byte hash
	 */
	public byte[] hash(String purpose, String value) {
		try {
			Mac mac = Mac.getInstance("HmacSHA256");
			mac.init(key);
			mac.update(purpose.getBytes(StandardCharsets.UTF_8));
			mac.update((byte) 0);
			return mac.doFinal(value.getBytes(StandardCharsets.UTF_8));
		}
		catch (GeneralSecurityException ex) {
			throw new IllegalStateException("Hashing failed", ex);
		}
	}
}
