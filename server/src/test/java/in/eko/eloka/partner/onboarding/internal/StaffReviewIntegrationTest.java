package in.eko.eloka.partner.onboarding.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Staff review over HTTP: roles, evidence decisions, corrections and maker-checker approval. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class StaffReviewIntegrationTest {

	private static final String REVIEWER = "9000000006";
	private static final String APPROVER = "9000000007";
	private static final String LEAD = "9000000008";

	@Autowired
	MockMvc mvc;

	@Autowired
	JsonMapper json;

	@Autowired
	JdbcTemplate jdbc;

	@Test
	void staffSignInAndSessionKindsStayApart() throws Exception {
		String reviewer = staffToken(REVIEWER);
		mvc.perform(get("/v1/admin/me").header("Authorization", "Bearer " + reviewer)).andExpect(status().isOk())
			.andExpect(jsonPath("$.roles[0]").value("REVIEWER"))
			.andExpect(jsonPath("$.email_masked").value("r***@example.com"))
			.andExpect(jsonPath("$.display_name").value("Sample Reviewer"));
		JourneyDriver applicant = new JourneyDriver(mvc, json);
		mvc.perform(get("/v1/admin/applications").header("Authorization", "Bearer " + applicant.token()))
			.andExpect(status().isForbidden());
		mvc.perform(get("/v1/partner/applications/current").header("Authorization", "Bearer " + reviewer))
			.andExpect(status().isForbidden());
		// A mobile that is not staff: no SMS, and no code can pass
		staffOtp("9000000099");
		mvc.perform(post("/v1/admin/auth/otp/verify").contentType(MediaType.APPLICATION_JSON)
			.content("{\"mobile\":\"9000000099\",\"otp\":\"123456\"}")).andExpect(status().isUnprocessableContent());
	}

	@Test
	void reviewThenApproveByASecondPerson() throws Exception {
		JourneyDriver applicant = new JourneyDriver(mvc, json).submit();
		String reviewer = staffToken(REVIEWER);
		String id = applicant.id();
		JsonNode queue = body(staff(reviewer, get("/v1/admin/applications")).andExpect(status().isOk()));
		assertThat(queue.findValuesAsString("application_id")).contains(id);

		staff(reviewer, post("/v1/admin/applications/" + id + "/approve")).andExpect(status().isForbidden());
		JsonNode view = body(staff(reviewer, post("/v1/admin/applications/" + id + "/claim")).andExpect(status().isOk())
			.andExpect(jsonPath("$.application.status").value("UNDER_REVIEW"))
			.andExpect(jsonPath("$.eligibility").value("ELIGIBLE"))
			.andExpect(jsonPath("$.approval_blockers").isNotEmpty()));
		staff(reviewer, recommend(id, "APPROVE")).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("NOT_READY"));

		verifyEverything(reviewer, id, view);
		staff(reviewer, recommend(id, "APPROVE")).andExpect(status().isOk()).andExpect(jsonPath("$.approval_blockers").isEmpty());

		staff(staffToken(APPROVER), post("/v1/admin/applications/" + id + "/approve")).andExpect(status().isOk())
			.andExpect(jsonPath("$.application.status").value("APPROVED"));
		applicant.refresh();
		applicant.call(post("/v1/partner/applications")).andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("ALREADY_APPROVED"));
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_event WHERE target_id = ? AND action = 'REVIEW_APPROVE'",
				Integer.class, id)).isEqualTo(1);
	}

	@Test
	void whoRecommendsCannotApprove() throws Exception {
		JourneyDriver applicant = new JourneyDriver(mvc, json).submit();
		String lead = staffToken(LEAD);
		String id = applicant.id();
		JsonNode view = body(staff(lead, post("/v1/admin/applications/" + id + "/claim")).andExpect(status().isOk()));
		verifyEverything(lead, id, view);
		staff(lead, recommend(id, "APPROVE")).andExpect(status().isOk());
		staff(lead, post("/v1/admin/applications/" + id + "/approve")).andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("SAME_PERSON"));
	}

	@Test
	void correctionsSendStepsBackAndFreezeTheRest() throws Exception {
		JourneyDriver applicant = new JourneyDriver(mvc, json).submit();
		String reviewer = staffToken(REVIEWER);
		String id = applicant.id();
		JsonNode view = body(staff(reviewer, post("/v1/admin/applications/" + id + "/claim")));
		String passport = evidenceId(view, "PASSPORT_PHOTO");
		staff(reviewer, post("/v1/admin/applications/" + id + "/documents/" + passport + "/decision")
			.contentType(MediaType.APPLICATION_JSON).content("{\"verified\":false}")).andExpect(status().isUnprocessableContent());
		staff(reviewer, post("/v1/admin/applications/" + id + "/documents/" + passport + "/decision")
			.contentType(MediaType.APPLICATION_JSON).content("{\"verified\":false,\"reason_code\":\"not a code at all\"}"))
			.andExpect(status().isUnprocessableContent());
		staff(reviewer, post("/v1/admin/applications/" + id + "/documents/" + passport + "/decision")
			.contentType(MediaType.APPLICATION_JSON).content("{\"verified\":false,\"reason_code\":\"BLURRY\"}"))
			.andExpect(status().isOk());
		staff(reviewer, post("/v1/admin/applications/" + id + "/corrections").contentType(MediaType.APPLICATION_JSON)
			.content("{\"steps\":[\"PHOTOS\"],\"reason_code\":\"BLURRY\",\"message\":\"Upload a clearer passport photo\"}"))
			.andExpect(status().isOk()).andExpect(jsonPath("$.application.status").value("CORRECTION_REQUIRED"));

		JsonNode snapshot = applicant.refresh();
		assertThat(snapshot.get("next_step").asString()).isEqualTo("PHOTOS");
		applicant.upload("COMPUTER_CERTIFICATE", JourneyDriver.jpeg(800, 600)).andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("STEP_NOT_EDITABLE"));
		applicant.upload("PASSPORT_PHOTO", JourneyDriver.jpeg(400, 500)).andExpect(status().isCreated());
		applicant.submitStep("/photos/submit", null);
		applicant.declareAndSubmit();
		assertThat(applicant.refresh().get("status").asString()).isEqualTo("SUBMITTED");
	}

	@Test
	void onlyApproversSeeHiddenValuesAndEveryRevealIsAudited() throws Exception {
		JourneyDriver applicant = new JourneyDriver(mvc, json).submit();
		String id = applicant.id();
		String body = "{\"field\":\"PAN\",\"reason\":\"Name check with the bank\"}";
		staff(staffToken(REVIEWER), post("/v1/admin/applications/" + id + "/pii-reveal").contentType(MediaType.APPLICATION_JSON)
			.content(body)).andExpect(status().isForbidden());
		staff(staffToken(APPROVER), post("/v1/admin/applications/" + id + "/pii-reveal").contentType(MediaType.APPLICATION_JSON)
			.content(body)).andExpect(status().isOk()).andExpect(jsonPath("$.value").value(applicant.pan()));
		assertThat(jdbc.queryForObject("SELECT details FROM audit_event WHERE target_id = ? AND action = 'PII_REVEALED'",
				String.class, id)).contains("PAN").doesNotContain(applicant.pan()).doesNotContain("bank");
	}

	private void verifyEverything(String token, String id, JsonNode view) throws Exception {
		for (JsonNode item : view.get("evidence")) {
			String status = item.get("status").asString();
			if (status.equals("VERIFIED")) {
				continue;
			}
			String path = item.get("type").asString().equals("DOCUMENT") ? "/documents/" : "/verifications/";
			staff(token, post("/v1/admin/applications/" + id + path + item.get("id").asString() + "/decision")
				.contentType(MediaType.APPLICATION_JSON).content("{\"verified\":true}")).andExpect(status().isOk());
		}
		staff(token, post("/v1/admin/applications/" + id + "/address/decision").contentType(MediaType.APPLICATION_JSON)
			.content("{\"verified\":true}")).andExpect(status().isOk());
	}

	private static String evidenceId(JsonNode view, String kind) {
		for (JsonNode item : view.get("evidence")) {
			if (item.get("kind").asString().equals(kind)) {
				return item.get("id").asString();
			}
		}
		throw new AssertionError("no " + kind);
	}

	private MockHttpServletRequestBuilder recommend(String id, String decision) {
		return post("/v1/admin/applications/" + id + "/recommendation").contentType(MediaType.APPLICATION_JSON)
			.content("{\"decision\":\"" + decision + "\"}");
	}

	private String staffToken(String mobile) throws Exception {
		jdbc.update("DELETE FROM otp_challenge WHERE purpose = 'STAFF_SIGN_IN'"); // past the resend gap between tests
		staffOtp(mobile);
		return body(mvc.perform(post("/v1/admin/auth/otp/verify").contentType(MediaType.APPLICATION_JSON)
			.content("{\"mobile\":\"" + mobile + "\",\"otp\":\"123456\"}")).andExpect(status().isOk())).get("access_token").asString();
	}

	private void staffOtp(String mobile) throws Exception {
		mvc.perform(post("/v1/admin/auth/otp").contentType(MediaType.APPLICATION_JSON).content("{\"mobile\":\"" + mobile + "\"}"))
			.andExpect(status().isAccepted());
	}

	private ResultActions staff(String token, MockHttpServletRequestBuilder request) throws Exception {
		return mvc.perform(request.header("Authorization", "Bearer " + token));
	}

	private JsonNode body(ResultActions result) throws Exception {
		return json.readTree(result.andReturn().getResponse().getContentAsString());
	}
}
