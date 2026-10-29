package in.eko.eloka.partner.onboarding.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import in.eko.eloka.partner.ApplicantClient;
import in.eko.eloka.partner.JourneyDriver;
import in.eko.eloka.partner.TestcontainersConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * The voter ID or driving licence check (fake providers: answers by the last 4 digits, see
 * FakeIdentityProviders): real failures use an attempt, outages do not, each document type has 3
 * tries, both used up block the step, and one document belongs to one live application.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AlternateIdIntegrationTest {

	@Autowired
	MockMvc mvc;

	@Autowired
	JsonMapper json;

	@Autowired
	org.springframework.jdbc.core.JdbcTemplate jdbc;

	private ApplicantClient client;
	private String token;
	private String id;
	private long version;

	@BeforeEach
	void reachTheStep() throws Exception {
		client = new ApplicantClient(mvc, json);
		token = client.signInNewApplicant();
		track(call(post("/v1/partner/applications")));
		String ref = client.body(call(post(app("/email-verifications")).contentType(MediaType.APPLICATION_JSON)
			.content("{\"email\":\"" + id.toLowerCase() + "@example.com\"}"))).get("challenge_ref").asString();
		track(write("/email-verifications/verify", "{\"challenge_ref\":\"" + ref + "\",\"otp\":\"123456\"}"));
		consent("DATA_PROCESSING");
		consent("DIGILOCKER");
		String url = client.body(call(post(app("/digilocker-sessions")).header("If-Match", etag()))).get("url").asString();
		track(call(post(app("/digilocker-sessions/complete")).contentType(MediaType.APPLICATION_JSON)
			.content("{\"state\":\"" + url.substring(url.indexOf("state=") + 6) + "\"}")));
		track(write("/pan-verifications", "{\"pan\":\"" + JourneyDriver.newPan("4321") + "\"}"));
		consent("CREDIT_CHECK");
		track(write("/credit-assessments", null).andExpect(jsonPath("$.next_step").value("ALTERNATE_ID")));
	}

	@Test
	void realFailuresUseAnAttemptAndOutagesDoNot() throws Exception {
		track(check("VOTER_ID", JourneyDriver.newEpic("0000")).andExpect(status().isOk())
			.andExpect(step("last_error_code").value("VOTER_ID_NOT_FOUND"))
			.andExpect(step("attempt_count").value(1)));
		track(check("VOTER_ID", JourneyDriver.newEpic("1111")).andExpect(step("last_error_code").value("VOTER_ID_NAME_MISMATCH")));
		track(check("DRIVING_LICENCE", JourneyDriver.newLicence("2222"))
			.andExpect(step("last_error_code").value("DRIVING_LICENCE_DOB_MISMATCH")));
		track(check("DRIVING_LICENCE", JourneyDriver.newLicence("3333"))
			.andExpect(step("last_error_code").value("DRIVING_LICENCE_NOT_ACTIVE"))
			.andExpect(step("attempt_count").value(4)));
		track(check("DRIVING_LICENCE", JourneyDriver.newLicence("9999"))
			.andExpect(step("last_error_code").value("ALTERNATE_ID_UNAVAILABLE"))
			.andExpect(step("attempt_count").value(4)));
		check("VOTER_ID", "AB12").andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.invalid_params.number").exists());
		check("PASSPORT", "X1234567").andExpect(status().isUnprocessableContent());
		track(check("DRIVING_LICENCE", JourneyDriver.newLicence("4321")).andExpect(status().isOk())
			.andExpect(step("status").value("COMPLETED"))
			.andExpect(jsonPath("$.next_step").value("PHOTOS")));
	}

	@Test
	void eachDocumentHasThreeTriesAndBothUsedUpBlockTheStep() throws Exception {
		for (int i = 0; i < 3; i++) {
			track(check("VOTER_ID", JourneyDriver.newEpic("0000")).andExpect(status().isOk()));
		}
		check("VOTER_ID", JourneyDriver.newEpic("4567")).andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("ID_TYPE_EXHAUSTED"));
		for (int i = 0; i < 3; i++) {
			track(check("DRIVING_LICENCE", JourneyDriver.newLicence("0000")).andExpect(status().isOk()));
		}
		check("DRIVING_LICENCE", JourneyDriver.newLicence("4321")).andExpect(status().isLocked());
	}

	@Test
	void anOutageDoesNotUndoAVerifiedDocument() throws Exception {
		track(check("VOTER_ID", JourneyDriver.newEpic("4567")).andExpect(step("status").value("COMPLETED")));
		track(check("DRIVING_LICENCE", JourneyDriver.newLicence("9999")).andExpect(status().isOk())
			.andExpect(step("status").value("COMPLETED")));
	}

	@Test
	void oneDocumentBelongsToOneLiveApplication() throws Exception {
		String epic = JourneyDriver.newEpic("4567");
		track(check("VOTER_ID", epic).andExpect(step("status").value("COMPLETED")));

		reachTheStep(); // a second applicant
		track(check("VOTER_ID", epic).andExpect(step("last_error_code").value("ID_ALREADY_USED")));
	}

	@Test
	void whenEveryTryIsUsedOurTeamChecksTheDocumentByHand() throws Exception {
		// not while an automatic check can still run
		write("/alternate-id-reviews", "{\"type\":\"VOTER_ID\",\"number\":\"" + JourneyDriver.newEpic("4567") + "\"}")
			.andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("MANUAL_CHECK_NOT_AVAILABLE"));
		for (int i = 0; i < 3; i++) {
			track(check("VOTER_ID", JourneyDriver.newEpic("0000")).andExpect(status().isOk()));
			track(check("DRIVING_LICENCE", JourneyDriver.newLicence("0000")).andExpect(status().isOk()));
		}
		track(call(get(app(""))).andExpect(step("status").value("BLOCKED")));
		write("/alternate-id-reviews", "{\"type\":\"VOTER_ID\",\"number\":\"AB12\"}").andExpect(status().isUnprocessableContent());

		String epic = JourneyDriver.newEpic("7777");
		track(write("/alternate-id-reviews", "{\"type\":\"VOTER_ID\",\"number\":\"" + epic + "\"}").andExpect(status().isOk())
			.andExpect(step("status").value("COMPLETED"))
			.andExpect(step("summary.check").value("STAFF"))
			.andExpect(step("summary.number_masked").value("******7777"))
			.andExpect(jsonPath("$.next_step").value("PHOTOS")));
		// waiting for a person: never verified by itself, so approval stays blocked until staff decide
		assertThat(jdbc.queryForObject("SELECT v.status FROM verification v JOIN partner_application a ON a.id = v.application_id "
				+ "WHERE a.public_id = ? AND v.kind = 'VOTER_ID' AND v.method = 'MANUAL'", String.class, id))
			.isEqualTo("MANUAL_REVIEW_REQUIRED");
		// and only once
		write("/alternate-id-reviews", "{\"type\":\"VOTER_ID\",\"number\":\"" + epic + "\"}")
			.andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("MANUAL_CHECK_NOT_AVAILABLE"));
	}

	private ResultActions check(String type, String number) throws Exception {
		return write("/alternate-id-verifications", "{\"type\":\"" + type + "\",\"number\":\"" + number + "\"}");
	}

	private static org.springframework.test.web.servlet.result.JsonPathResultMatchers step(String field) {
		return jsonPath("$.steps[?(@.step_key=='ALTERNATE_ID')]." + field);
	}

	private ResultActions write(String path, String body) throws Exception {
		MockHttpServletRequestBuilder request = post(app(path)).header("If-Match", etag())
			.header("Idempotency-Key", UUID.randomUUID().toString());
		if (body != null) {
			request.contentType(MediaType.APPLICATION_JSON).content(body);
		}
		return call(request);
	}

	private void consent(String purpose) throws Exception {
		track(call(post(app("/consents")).header("If-Match", etag()).contentType(MediaType.APPLICATION_JSON)
			.content("{\"purpose\":\"" + purpose + "\",\"version\":\"2026-10-27\"}")));
	}

	private void track(ResultActions result) throws Exception {
		JsonNode snapshot = client.body(result.andExpect(status().is2xxSuccessful()));
		id = snapshot.get("application_id").asString();
		version = snapshot.get("version").asLong();
	}

	private String etag() {
		return "\"v" + version + "\"";
	}

	private String app(String path) {
		return "/v1/partner/applications/" + id + path;
	}

	private ResultActions call(MockHttpServletRequestBuilder request) throws Exception {
		return mvc.perform(request.header("Authorization", "Bearer " + token));
	}
}
