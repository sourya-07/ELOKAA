package in.eko.eloka.partner.onboarding.internal;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
	void withdrawNeedsAFreshCode() throws Exception {
		JourneyDriver applicant = new JourneyDriver(mvc, json).submitStepByStep();
		long version = applicant.refresh().get("version").asLong();
		String withdraw = "/v1/partner/applications/" + applicant.id() + "/withdraw";

		applicant.call(post(withdraw).header("If-Match", "\"v" + version + "\"").contentType(MediaType.APPLICATION_JSON)
			.content("{\"otp\":\"123456\"}")).andExpect(status().isGone()); // no code asked for yet
		applicant.call(post("/v1/partner/reauth")).andExpect(status().isAccepted());
		applicant.call(post(withdraw).header("If-Match", "\"v" + version + "\"").contentType(MediaType.APPLICATION_JSON)
			.content("{\"otp\":\"000000\"}")).andExpect(status().isUnprocessableContent())
			.andExpect(jsonPath("$.attempts_left").value(4));
		applicant.call(post(withdraw).header("If-Match", "\"v" + version + "\"").contentType(MediaType.APPLICATION_JSON)
			.content("{\"otp\":\"123456\",\"reason_code\":\"CHANGED_MIND\"}")).andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("WITHDRAWN"));
	}
}
