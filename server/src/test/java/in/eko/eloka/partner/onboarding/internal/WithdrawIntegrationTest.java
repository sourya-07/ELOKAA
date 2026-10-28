package in.eko.eloka.partner.onboarding.internal;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import in.eko.eloka.partner.JourneyDriver;
import in.eko.eloka.partner.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.json.JsonMapper;

/** Withdrawing needs a fresh code to the applicant's own mobile. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class WithdrawIntegrationTest {

	@Autowired
	MockMvc mvc;

	@Autowired
	JsonMapper json;

	@Test
	void withdrawNeedsAFreshCodeAndARetryReplays() throws Exception {
		JourneyDriver applicant = new JourneyDriver(mvc, json).submit();
		long version = applicant.refresh().get("version").asLong();
		String withdraw = "/v1/partner/applications/" + applicant.id() + "/withdraw";

		applicant.call(withdraw(withdraw, version, "wd-1", "123456")).andExpect(status().isGone()); // no code asked for
		applicant.call(post("/v1/partner/reauth")).andExpect(status().isAccepted());
		applicant.call(withdraw(withdraw, version, "wd-2", "000000")).andExpect(status().isUnprocessableContent())
			.andExpect(jsonPath("$.attempts_left").value(4));
		// A stale version is refused before the code is checked, so the code still works afterwards
		applicant.call(withdraw(withdraw, version - 1, "wd-3", "123456")).andExpect(status().isPreconditionFailed());
		applicant.call(withdraw(withdraw, version, "wd-4", "123456")).andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("WITHDRAWN"));
		// The answer was lost: the same request again replays it instead of checking the used code
		applicant.call(withdraw(withdraw, version, "wd-4", "123456")).andExpect(status().isOk())
			.andExpect(header().string("Idempotent-Replayed", "true"))
			.andExpect(jsonPath("$.status").value("WITHDRAWN"));
	}

	private static MockHttpServletRequestBuilder withdraw(String path, long version, String key, String otp) {
		return post(path).header("If-Match", "\"v" + version + "\"").header("Idempotency-Key", key)
			.contentType(MediaType.APPLICATION_JSON).content("{\"otp\":\"" + otp + "\",\"reason_code\":\"CHANGED_MIND\"}");
	}
}
