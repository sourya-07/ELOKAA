package in.eko.eloka.partner.shared;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import in.eko.eloka.partner.shared.crypto.BlindIndex;
import in.eko.eloka.partner.shared.crypto.FieldCipher;
import in.eko.eloka.partner.shared.crypto.Masking;
import in.eko.eloka.partner.shared.crypto.Mobiles;
import in.eko.eloka.partner.shared.error.ValidationException;
import org.junit.jupiter.api.Test;

class CryptoTest {

	private static final String KEY = "cGFydG5lci10ZXN0LWZpZWxkLWtleS0zMi1ieXRlcyE=";
	private static final String OTHER_KEY = "cGFydG5lci10ZXN0LWluZGV4LWtleS0zMi1ieXRlcyE=";

	@Test
	void encryptsWithRandomNonceAndDecrypts() {
		FieldCipher cipher = new FieldCipher(KEY);
		byte[] first = cipher.encrypt("9876543210");
		byte[] second = cipher.encrypt("9876543210");
		assertThat(first).isNotEqualTo(second);
		assertThat(cipher.decrypt(first)).isEqualTo("9876543210");
	}

	@Test
	void tamperedCiphertextIsRejected() {
		FieldCipher cipher = new FieldCipher(KEY);
		byte[] sealed = cipher.encrypt("secret");
		sealed[sealed.length - 1] ^= 1;
		assertThatThrownBy(() -> cipher.decrypt(sealed)).isInstanceOf(IllegalStateException.class);
	}

	@Test
	void blindIndexIsStableAndKeyed() {
		assertThat(new BlindIndex(KEY).hash("mobile", "9876543210"))
			.isEqualTo(new BlindIndex(KEY).hash("mobile", "9876543210"))
			.isNotEqualTo(new BlindIndex(OTHER_KEY).hash("mobile", "9876543210"))
			.isNotEqualTo(new BlindIndex(KEY).hash("email", "9876543210"));
	}

	@Test
	void rejectsShortKeys() {
		assertThatThrownBy(() -> new FieldCipher("c2hvcnQ=")).isInstanceOf(IllegalStateException.class);
	}

	@Test
	void normalisesMobiles() {
		assertThat(Mobiles.normalize("+91 98765-43210")).isEqualTo("9876543210");
		assertThat(Mobiles.normalize("09876543210")).isEqualTo("9876543210");
		assertThatThrownBy(() -> Mobiles.normalize("12345")).isInstanceOf(ValidationException.class);
		assertThatThrownBy(() -> Mobiles.normalize("5876543210")).isInstanceOf(ValidationException.class);
	}

	@Test
	void masks() {
		assertThat(Masking.mobile("9876543210")).isEqualTo("******3210");
		assertThat(Masking.pan("ABCDE1234F")).isEqualTo("ABCDE****F");
		assertThat(Masking.account("123456789012")).isEqualTo("XXXXXXXX9012");
		assertThat(Masking.email("ravi@example.com")).isEqualTo("r***@example.com");
	}
}
