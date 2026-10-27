package in.eko.eloka.partner.shared.crypto;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * AES-256-GCM encryption for personal data stored in the database.
 * Output layout: {@code [1 byte version][12 byte nonce][ciphertext + 16 byte tag]}.
 * The version byte leaves room for key rotation.
 */
public final class FieldCipher {

	private static final byte VERSION = 1;
	private static final int NONCE_BYTES = 12;
	private static final int TAG_BITS = 128;

	private final SecretKeySpec key;
	private final SecureRandom random = new SecureRandom();

	public FieldCipher(String base64Key) {
		this.key = new SecretKeySpec(KeyMaterial.decode32(base64Key, "eloka.crypto.field-key"), "AES");
	}

	public byte[] encrypt(String plaintext) {
		return plaintext == null ? null : encryptBytes(plaintext.getBytes(StandardCharsets.UTF_8));
	}

	public String decrypt(byte[] stored) {
		return stored == null ? null : new String(decryptBytes(stored), StandardCharsets.UTF_8);
	}

	/** Encrypts arbitrary bytes (e.g. uploaded documents) with the same format. */
	public byte[] encryptBytes(byte[] plaintext) {
		try {
			byte[] nonce = new byte[NONCE_BYTES];
			random.nextBytes(nonce);
			Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
			cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, nonce));
			byte[] sealed = cipher.doFinal(plaintext);
			return ByteBuffer.allocate(1 + NONCE_BYTES + sealed.length).put(VERSION).put(nonce).put(sealed).array();
		}
		catch (GeneralSecurityException ex) {
			throw new IllegalStateException("Encryption failed", ex);
		}
	}

	public byte[] decryptBytes(byte[] stored) {
		try {
			ByteBuffer buffer = ByteBuffer.wrap(stored);
			byte version = buffer.get();
			if (version != VERSION) {
				throw new IllegalStateException("Unknown ciphertext version " + version);
			}
			byte[] nonce = new byte[NONCE_BYTES];
			buffer.get(nonce);
			byte[] sealed = new byte[buffer.remaining()];
			buffer.get(sealed);
			Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
			cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, nonce));
			return cipher.doFinal(sealed);
		}
		catch (GeneralSecurityException ex) {
			throw new IllegalStateException("Decryption failed", ex);
		}
	}

	/** Helpers shared by the crypto classes. */
	static final class KeyMaterial {

		private KeyMaterial() {
		}

		static byte[] decode32(String base64, String property) {
			if (base64 == null || base64.isBlank()) {
				throw new IllegalStateException(property + " is not set");
			}
			byte[] bytes = Base64.getDecoder().decode(base64.trim());
			if (bytes.length != 32) {
				throw new IllegalStateException(property + " must be 32 bytes (base64), got " + bytes.length);
			}
			return bytes;
		}
	}
}
