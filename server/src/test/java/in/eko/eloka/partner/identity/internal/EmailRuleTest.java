package in.eko.eloka.partner.identity.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import in.eko.eloka.partner.shared.error.ValidationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/** The same cases as the client's normaliseEmail test (client/__tests__/lib/validation.test.ts). */
class EmailRuleTest {

	@ParameterizedTest(name = "{0}")
	@CsvSource(delimiter = '|', quoteCharacter = '"', value = {
			"name@example.com | name@example.com",
			"\"  Name.Surname+tag@Example.CO.IN \" | name.surname+tag@example.co.in",
			"o'brien@example.org | o'brien@example.org",
			"user@sub.domain.example | user@sub.domain.example" })
	void acceptsAndNormalises(String raw, String expected) {
		assertThat(EmailVerificationService.normaliseAddress(raw)).isEqualTo(expected);
	}

	@ParameterizedTest(name = "{0}")
	@ValueSource(strings = { "name.example.com", "name@example", "name@example.c", "name @example.com", "@example.com",
			"a@b@example.com" })
	void refuses(String raw) {
		assertThatThrownBy(() -> EmailVerificationService.normaliseAddress(raw)).isInstanceOf(ValidationException.class);
	}

	@Test
	void refusesOverlongParts() {
		assertThatThrownBy(() -> EmailVerificationService.normaliseAddress("a".repeat(65) + "@example.com"))
			.isInstanceOf(ValidationException.class);
		assertThatThrownBy(() -> EmailVerificationService.normaliseAddress("a@" + "b".repeat(250) + ".com"))
			.isInstanceOf(ValidationException.class);
	}
}
