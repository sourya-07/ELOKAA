package in.eko.eloka.partner.shared.persistence;

import java.security.SecureRandom;
import java.time.Instant;

/**
 * Readable public ids like {@code ptn_01JABC...}: a prefix plus 26 Crockford base32 characters
 * (48-bit millisecond time + 80 random bits, like a ULID). Time-ordered, not guessable, and
 * independent of the database row id.
 */
public final class PublicIds {

	private static final char[] ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ".toCharArray();
	private static final SecureRandom RANDOM = new SecureRandom();

	private PublicIds() {
	}

	public static String next(String prefix, Instant now) {
		byte[] random = new byte[10];
		RANDOM.nextBytes(random);
		long time = now.toEpochMilli();
		char[] out = new char[26];
		// 10 characters of time (50 bits, top 2 always 0), then 16 characters of randomness (80 bits).
		for (int i = 9; i >= 0; i--) {
			out[i] = ALPHABET[(int) (time & 31)];
			time >>>= 5;
		}
		long hi = 0;
		for (int i = 0; i < 5; i++) {
			hi = (hi << 8) | (random[i] & 0xFF);
		}
		long lo = 0;
		for (int i = 5; i < 10; i++) {
			lo = (lo << 8) | (random[i] & 0xFF);
		}
		for (int i = 17; i >= 10; i--) {
			out[i] = ALPHABET[(int) (hi & 31)];
			hi >>>= 5;
		}
		for (int i = 25; i >= 18; i--) {
			out[i] = ALPHABET[(int) (lo & 31)];
			lo >>>= 5;
		}
		return prefix + "_" + new String(out);
	}
}
