package in.eko.eloka.partner.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import in.eko.eloka.partner.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Applicant sign-in end to end, with the fake OTP provider (code 123456) and real MySQL. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ApplicantSignInIntegrationTest {

	/** A fresh mobile per test, so the per-mobile limits of one test do not affect another. */
	private static final AtomicLong MOBILES = new AtomicLong(9_100_000_000L);

	@Autowired
	MockMvc mvc;

	@Autowired
	JsonMapper json;

	@Autowired
	JdbcTemplate jdbc;

	@Test
	void signInSignOut() throws Exception {
		String mobile = nextMobile();
		JsonNode sent = body(sendOtp(mobile).andExpect(status().isAccepted())
			.andExpect(jsonPath("$.otp_length").value(6))
			.andExpect(jsonPath("$.expires_in").value(300)));

		JsonNode tokens = body(verify(sent.get("otp_ref").asString(), "123456").andExpect(status().isOk())
			.andExpect(jsonPath("$.token_type").value("Bearer")));
		String access = tokens.get("access_token").asString();
		assertThat(access).startsWith("ptn_at_");

		mvc.perform(delete("/v1/partner/session").header("Authorization", "Bearer " + access))
			.andExpect(status().isNoContent());
		mvc.perform(delete("/v1/partner/session").header("Authorization", "Bearer " + access))
			.andExpect(status().isUnauthorized());
	}

	@Test
	void secondSignInReusesTheApplicant() throws Exception {
		String mobile = nextMobile();
		signIn(mobile);
		Integer applicants = jdbc.queryForObject("SELECT COUNT(*) FROM applicant", Integer.class);
		jdbc.update("UPDATE otp_challenge SET created_at = created_at - INTERVAL 1 MINUTE"); // past the resend gap
		signIn(mobile);
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM applicant", Integer.class)).isEqualTo(applicants);
	}

	@Test
	void wrongCodesCountDownThenLockTheOtp() throws Exception {
		String ref = body(sendOtp(nextMobile())).get("otp_ref").asString();
		for (int left = 4; left >= 1; left--) {
			verify(ref, "000000").andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.code").value("OTP_INVALID"))
				.andExpect(jsonPath("$.attempts_left").value(left));
		}
		verify(ref, "000000").andExpect(status().isTooManyRequests()).andExpect(jsonPath("$.code").value("OTP_LOCKED"));
		// Even the right code is refused now: a new OTP is needed
		verify(ref, "123456").andExpect(status().isGone()).andExpect(jsonPath("$.code").value("OTP_EXPIRED"));
	}

	@Test
	void malformedCodesCountAsWrong() throws Exception {
		String ref = body(sendOtp(nextMobile())).get("otp_ref").asString();
		verify(ref, "12345x").andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.attempts_left").value(4));
	}

	@Test
	void aUsedOtpCannotBeUsedAgain() throws Exception {
		String ref = body(sendOtp(nextMobile())).get("otp_ref").asString();
		verify(ref, "123456").andExpect(status().isOk());
		verify(ref, "123456").andExpect(status().isGone());
	}

	@Test
	void resendingTooSoonIsLimited() throws Exception {
		String mobile = nextMobile();
		sendOtp(mobile).andExpect(status().isAccepted()).andExpect(jsonPath("$.resend_after").value(60));
		// the server holds the 60-second gap whatever the client shows
		sendOtp(mobile).andExpect(status().isTooManyRequests())
			.andExpect(jsonPath("$.code").value("OTP_LIMIT_REACHED"))
			.andExpect(jsonPath("$.retry_after").value(org.hamcrest.Matchers.allOf(
					org.hamcrest.Matchers.greaterThan(50), org.hamcrest.Matchers.lessThanOrEqualTo(60))));
	}

	@Test
	void parallelSendsForOneMobileSendOnlyOneOtp() throws Exception {
		String mobile = nextMobile();
		ExecutorService pool = Executors.newFixedThreadPool(6);
		try {
			List<Future<Integer>> results = new ArrayList<>();
			for (int i = 0; i < 6; i++) {
				results.add(pool.submit(() -> sendOtp(mobile).andReturn().getResponse().getStatus()));
			}
			List<Integer> statuses = new ArrayList<>();
			for (Future<Integer> result : results) {
				statuses.add(result.get(30, TimeUnit.SECONDS));
			}
			assertThat(statuses).containsOnlyOnce(202).containsOnly(202, 429);
		}
		finally {
			pool.shutdownNow();
		}
	}

	@Test
	void invalidInputIsRejected() throws Exception {
		sendOtp("12345").andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
		verify("otpref_tampered", "123456").andExpect(status().isUnprocessableContent())
			.andExpect(jsonPath("$.invalid_params.otp_ref").exists());
	}

	@Test
	void refreshRotatesAndAReusedRefreshTokenEndsTheSession() throws Exception {
		JsonNode first = signIn(nextMobile());
		JsonNode second = body(refresh(first.get("refresh_token").asString()).andExpect(status().isOk()));
		assertThat(second.get("access_token").asString()).isNotEqualTo(first.get("access_token").asString());

		// The old refresh token again: treated as stolen, the whole session ends
		refresh(first.get("refresh_token").asString()).andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("SESSION_EXPIRED"));
		mvc.perform(delete("/v1/partner/session").header("Authorization", "Bearer " + second.get("access_token").asString()))
			.andExpect(status().isUnauthorized());
	}

	@Test
	void protectedRoutesNeedAnApplicantSession() throws Exception {
		mvc.perform(delete("/v1/partner/session")).andExpect(status().isUnauthorized());
		mvc.perform(delete("/v1/partner/session").header("Authorization", "Bearer ptn_at_madeup"))
			.andExpect(status().isUnauthorized());
	}

	private JsonNode signIn(String mobile) throws Exception {
		String ref = body(sendOtp(mobile).andExpect(status().isAccepted())).get("otp_ref").asString();
		return body(verify(ref, "123456").andExpect(status().isOk()));
	}

	private ResultActions sendOtp(String mobile) throws Exception {
		return mvc.perform(post("/v1/partner/otp").contentType(MediaType.APPLICATION_JSON)
			.content("{\"mobile\":\"" + mobile + "\"}"));
	}

	private ResultActions verify(String ref, String otp) throws Exception {
		return mvc.perform(post("/v1/partner/otp/verify").contentType(MediaType.APPLICATION_JSON)
			.content("{\"otp_ref\":\"" + ref + "\",\"otp\":\"" + otp + "\"}"));
	}

	private ResultActions refresh(String token) throws Exception {
		return mvc.perform(post("/v1/partner/session/refresh").contentType(MediaType.APPLICATION_JSON)
			.content("{\"refresh_token\":\"" + token + "\"}"));
	}

	private JsonNode body(ResultActions result) throws Exception {
		return json.readTree(result.andReturn().getResponse().getContentAsString());
	}

	private static String nextMobile() {
		return Long.toString(MOBILES.incrementAndGet());
	}
}
