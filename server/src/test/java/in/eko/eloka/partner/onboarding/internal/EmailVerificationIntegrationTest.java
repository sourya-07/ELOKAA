package in.eko.eloka.partner.onboarding.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import in.eko.eloka.partner.ApplicantClient;
import in.eko.eloka.partner.TestcontainersConfiguration;
import org.junit.jupiter.api.BeforeEach;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.json.JsonMapper;

/**
 * Email codes over HTTP (test profile: the fixed code 123456, no mail sent): masking, wrong and
 * expired codes, limits, single use, one address per live application and another applicant's id.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class EmailVerificationIntegrationTest {

	@Autowired
	MockMvc mvc;

	@Autowired
	JsonMapper json;

	@Autowired
	JdbcTemplate jdbc;

	private ApplicantClient client;
	private String token;
	private String id;

	@BeforeEach
	void start() throws Exception {
		client = new ApplicantClient(mvc, json);
		token = client.signInNewApplicant();
		id = client.body(call(post("/v1/partner/applications"))).get("application_id").asString();
	}

	@Test
	void aCodeProvesTheAddressOnceAndTheDeclarationFollowsIt() throws Exception {
		String email = unique();
		String ref = client.body(send(email.toUpperCase()).andExpect(status().isAccepted())
			.andExpect(jsonPath("$.otp_length").value(6))
			.andExpect(jsonPath("$.expires_in").value(600))
			.andExpect(jsonPath("$.email_masked").value(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString(email)))))
			.get("challenge_ref").asString();
		verify(ref, "000000", 0).andExpect(status().isUnprocessableContent())
			.andExpect(jsonPath("$.code").value("OTP_INVALID"))
			.andExpect(jsonPath("$.attempts_left").value(4));
		verify(ref, "123456", 0).andExpect(status().isOk())
			.andExpect(jsonPath("$.next_step").value("CONSENT"))
			.andExpect(jsonPath("$.steps[?(@.step_key=='EMAIL_VERIFICATION')].status").value("COMPLETED"))
			.andExpect(jsonPath("$.steps[?(@.step_key=='EMAIL_VERIFICATION')].summary.email_masked").exists());
		// single use: the same code again is gone
		verify(ref, "123456", 1).andExpect(status().isGone()).andExpect(jsonPath("$.code").value("OTP_EXPIRED"));

		String stored = jdbc.queryForObject("SELECT email_masked FROM partner_application WHERE public_id = ?", String.class, id);
		assertThat(stored).doesNotContain(email.substring(0, email.indexOf('@')));
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_event WHERE action = 'EMAIL_VERIFIED' AND target_id = ?",
				Integer.class, id)).isEqualTo(1);
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_event WHERE details LIKE ?", Integer.class,
				"%" + email + "%")).isZero();
	}

	@Test
	void fiveWrongCodesLockItAndExpiredOrForeignCodesFail() throws Exception {
		String ref = client.body(send(unique()).andExpect(status().isAccepted())).get("challenge_ref").asString();
		for (int i = 0; i < 4; i++) {
			verify(ref, "000000", 0).andExpect(status().isUnprocessableContent());
		}
		verify(ref, "000000", 0).andExpect(status().isTooManyRequests()).andExpect(jsonPath("$.code").value("OTP_LOCKED"));
		verify(ref, "123456", 0).andExpect(status().isGone());
		verify("not-a-ref", "123456", 0).andExpect(status().isGone());

		jdbc.update("UPDATE email_verification_challenge SET created_at = NOW(6) - INTERVAL 1 MINUTE"); // past the resend gap
		String fresh = client.body(send(unique()).andExpect(status().isAccepted())).get("challenge_ref").asString();
		jdbc.update("UPDATE email_verification_challenge SET expires_at = NOW(6) - INTERVAL 1 SECOND WHERE id = UUID_TO_BIN(?)",
				fresh);
		verify(fresh, "123456", 0).andExpect(status().isGone()).andExpect(jsonPath("$.code").value("OTP_EXPIRED"));
	}

	@Test
	void sendsAreLimitedAndAddressesChecked() throws Exception {
		send("not an email").andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.invalid_params.email").exists());
		String email = unique();
		send(email).andExpect(status().isAccepted());
		send(email).andExpect(status().isTooManyRequests()).andExpect(jsonPath("$.code").value("OTP_LIMIT_REACHED"))
			.andExpect(jsonPath("$.retry_after").exists());
	}

	@Test
	void oneAddressPerLiveApplication() throws Exception {
		String email = unique();
		String ref = client.body(send(email).andExpect(status().isAccepted())).get("challenge_ref").asString();
		verify(ref, "123456", 0).andExpect(status().isOk());

		token = client.signInNewApplicant();
		id = client.body(call(post("/v1/partner/applications"))).get("application_id").asString();
		send(email).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("EMAIL_ALREADY_USED"));
	}

	@Test
	void anotherApplicantsApplicationIsNotFound() throws Exception {
		String mine = id;
		token = client.signInNewApplicant();
		call(post("/v1/partner/applications/" + mine + "/email-verifications").contentType(MediaType.APPLICATION_JSON)
			.content("{\"email\":\"" + unique() + "\"}")).andExpect(status().isNotFound());
	}

	private ResultActions send(String email) throws Exception {
		return call(post("/v1/partner/applications/" + id + "/email-verifications").contentType(MediaType.APPLICATION_JSON)
			.content("{\"email\":\"" + email + "\"}"));
	}

	private ResultActions verify(String ref, String code, long version) throws Exception {
		return call(post("/v1/partner/applications/" + id + "/email-verifications/verify").header("If-Match", "\"v" + version + "\"")
			.header("Idempotency-Key", UUID.randomUUID().toString())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"challenge_ref\":\"" + ref + "\",\"otp\":\"" + code + "\"}"));
	}

	private static String unique() {
		return "applicant." + UUID.randomUUID().toString().substring(0, 12) + "@example.com";
	}

	private ResultActions call(MockHttpServletRequestBuilder request) throws Exception {
		return mvc.perform(request.header("Authorization", "Bearer " + token));
	}
}
