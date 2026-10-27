package in.eko.eloka.partner.shared;

import static org.assertj.core.api.Assertions.assertThat;

import in.eko.eloka.partner.shared.logging.PiiMasker;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class PiiMaskerTest {

	@ParameterizedTest(name = "{0}")
	@CsvSource(delimiter = '|', value = {
			"mobile 9876543210 here | mobile ******3210 here",
			"with +919876543210 | with ******3210",
			"pan ABCDE1234F done | pan [PAN] done",
			"aadhaar 1234 5678 9012 | aadhaar [AADHAAR]",
			"acct 123456789012345 x | acct [NUMBER] x",
			"Authorization: Bearer abc.def-ghi | Authorization: Bearer [REDACTED]",
			"token ptn_at_AbC123_-x end | token ptn_at_[REDACTED] end",
			"{\"credit_score\":742} | {\"credit_score\":[REDACTED]}",
			"{\"otp\":\"123456\"} | {\"otp\":\"[REDACTED]\"}",
			"user_9876543210 | user_******3210",
			"code 123456 stays | code 123456 stays" })
	void masksPersonalData(String input, String expected) {
		assertThat(PiiMasker.mask(input)).isEqualTo(expected);
	}
}
