package in.eko.eloka.partner.onboarding.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
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
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** The application rules over HTTP, against MySQL 8.4. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PartnerApplicationIntegrationTest {

	private static final String CONSENT = "{\"purpose\":\"DATA_PROCESSING\",\"version\":\"2026-10-27\"}";

	@Autowired
	MockMvc mvc;

	@Autowired
	JsonMapper json;

	@Autowired
	JdbcTemplate jdbc;

	@Autowired
	ExpiryJob expiryJob;

	private ApplicantClient client;
	private String token;

	@BeforeEach
	void signIn() throws Exception {
		client = new ApplicantClient(mvc, json);
		token = client.signInNewApplicant();
	}

	@Test
	void startThenResume() throws Exception {
		JsonNode created = client.body(call(post("/v1/partner/applications")).andExpect(status().isCreated())
			.andExpect(header().string("ETag", "\"v0\""))
			.andExpect(jsonPath("$.status").value("IN_PROGRESS"))
			.andExpect(jsonPath("$.next_step").value("CONSENT"))
			.andExpect(jsonPath("$.steps.length()").value(11))
			.andExpect(jsonPath("$.steps[0].step_key").value("MOBILE_VERIFICATION"))
			.andExpect(jsonPath("$.steps[0].status").value("COMPLETED")));
		String id = created.get("application_id").asString();
		assertThat(id).startsWith("ptn_");

		call(post("/v1/partner/applications")).andExpect(status().isOk())
			.andExpect(jsonPath("$.application_id").value(id));
		call(get("/v1/partner/applications/current")).andExpect(status().isOk())
			.andExpect(jsonPath("$.application_id").value(id));
	}

	@Test
	void stepsMoveInOrderAndTheServerSaysWhatIsNext() throws Exception {
		String id = start();
		call(put("/v1/partner/applications/" + id + "/method").header("If-Match", "\"v0\"")
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"method\":\"MANUAL\"}")).andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("PREREQUISITE_INCOMPLETE"))
			.andExpect(jsonPath("$.missing_steps[0]").value("CONSENT"));

		JsonNode afterConsent = client.body(consent(id, "\"v0\"", CONSENT).andExpect(status().isOk())
			.andExpect(jsonPath("$.next_step").value("VERIFICATION_METHOD")));
		call(put("/v1/partner/applications/" + id + "/method")
			.header("If-Match", "\"v" + afterConsent.get("version").asLong() + "\"")
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"method\":\"manual\"}")).andExpect(status().isOk())
			.andExpect(jsonPath("$.method").value("MANUAL"))
			.andExpect(jsonPath("$.next_step").value("AADHAAR"));
	}

	@Test
	void writesNeedTheCurrentVersion() throws Exception {
		String id = start();
		consent(id, null, CONSENT).andExpect(status().isPreconditionRequired());
		consent(id, "\"v0\"", CONSENT).andExpect(status().isOk());
		consent(id, "\"v0\"", CONSENT).andExpect(status().isPreconditionFailed())
			.andExpect(jsonPath("$.code").value("PRECONDITION_FAILED"))
			.andExpect(jsonPath("$.current.version").value(1));
	}

	@Test
	void outdatedConsentTextIsRefused() throws Exception {
		String id = start();
		consent(id, "\"v0\"", "{\"purpose\":\"DATA_PROCESSING\",\"version\":\"2025-01-01\"}")
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("CONSENT_VERSION_OUTDATED"));
		consent(id, "\"v0\"", "{\"purpose\":\"MARKETING\",\"version\":\"2026-10-27\"}")
			.andExpect(status().isUnprocessableContent());
	}

	@Test
	void submitNeedsEveryStepAndAnIdempotencyKey() throws Exception {
		String id = start();
		call(post("/v1/partner/applications/" + id + "/submit").header("If-Match", "\"v0\""))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REQUIRED"));
		call(post("/v1/partner/applications/" + id + "/submit").header("If-Match", "\"v0\"")
			.header("Idempotency-Key", UUID.randomUUID().toString())).andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("PREREQUISITE_INCOMPLETE"));
		call(post("/v1/partner/applications/" + id + "/declaration").header("If-Match", "\"v0\"")
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"version\":\"2026-10-27\",\"accepted\":true}")).andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("PREREQUISITE_INCOMPLETE"));
	}

	@Test
	void aDoubleSubmitSubmitsOnce() throws Exception {
		String id = start();
		jdbc.update("""
				UPDATE application_step s JOIN partner_application a ON a.id = s.application_id
				SET s.status = 'COMPLETED' WHERE a.public_id = ?""", id); // as if every check had passed
		jdbc.update("""
				INSERT INTO credit_assessment (id, application_id, pan_hash, provider, outcome, score, assessed_at,
				    version, created_at, updated_at)
				SELECT UUID_TO_BIN(UUID()), a.id, UNHEX(REPEAT('AB', 32)), 'test', 'ELIGIBLE', 742, NOW(6), 0, NOW(6), NOW(6)
				FROM partner_application a WHERE a.public_id = ?""", id);
		String key = UUID.randomUUID().toString();
		ExecutorService pool = Executors.newFixedThreadPool(2);
		try {
			List<Future<Integer>> results = new ArrayList<>();
			for (int i = 0; i < 2; i++) {
				results.add(pool.submit(() -> call(post("/v1/partner/applications/" + id + "/submit")
					.header("If-Match", "\"v0\"")
					.header("Idempotency-Key", key)).andReturn().getResponse().getStatus()));
			}
			List<Integer> statuses = new ArrayList<>();
			for (Future<Integer> result : results) {
				statuses.add(result.get(30, TimeUnit.SECONDS));
			}
			assertThat(statuses).contains(200).allMatch(s -> s == 200 || s == 409);
		}
		finally {
			pool.shutdownNow();
		}
		// The same key again replays the stored answer
		call(post("/v1/partner/applications/" + id + "/submit").header("If-Match", "\"v0\"")
			.header("Idempotency-Key", key)).andExpect(status().isOk())
			.andExpect(header().string("Idempotent-Replayed", "true"))
			.andExpect(jsonPath("$.status").value("SUBMITTED"));
		assertThat(jdbc.queryForObject(
				"SELECT COUNT(*) FROM application_event e JOIN partner_application a ON a.id = e.application_id "
						+ "WHERE a.public_id = ? AND e.to_status = 'SUBMITTED'", Integer.class, id)).isEqualTo(1);
		// Submitted: no more changes
		consent(id, "\"v1\"", CONSENT).andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("APPLICATION_NOT_EDITABLE"));
	}

	@Test
	void submitChecksEligibilityEvenWhenEveryStepLooksDone() throws Exception {
		String id = start();
		jdbc.update("""
				UPDATE application_step s JOIN partner_application a ON a.id = s.application_id
				SET s.status = 'COMPLETED' WHERE a.public_id = ?""", id);
		call(post("/v1/partner/applications/" + id + "/submit").header("If-Match", "\"v0\"")
			.header("Idempotency-Key", UUID.randomUUID().toString())).andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("NOT_ELIGIBLE"));
	}

	@Test
	void parallelStartsMakeOneApplication() throws Exception {
		ExecutorService pool = Executors.newFixedThreadPool(4);
		try {
			List<Future<String>> results = new ArrayList<>();
			for (int i = 0; i < 4; i++) {
				results.add(pool.submit(() -> client.body(call(post("/v1/partner/applications"))).get("application_id")
					.asString()));
			}
			List<String> ids = new ArrayList<>();
			for (Future<String> result : results) {
				ids.add(result.get(30, TimeUnit.SECONDS));
			}
			assertThat(ids).containsOnly(ids.get(0));
		}
		finally {
			pool.shutdownNow();
		}
	}

	@Test
	void anotherApplicantsApplicationIsNotFound() throws Exception {
		String id = start();
		String otherToken = client.signInNewApplicant();
		mvc.perform(get("/v1/partner/applications/" + id).header("Authorization", "Bearer " + otherToken))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("APPLICATION_NOT_FOUND"));
	}

	@Test
	void idleApplicationsExpireAndANewOneCanStart() throws Exception {
		String id = start();
		jdbc.update("UPDATE partner_application SET last_applicant_activity_at = NOW(6) - INTERVAL 31 DAY WHERE public_id = ?",
				id);
		expiryJob.run();
		call(get("/v1/partner/applications/" + id)).andExpect(jsonPath("$.status").value("EXPIRED"));
		call(post("/v1/partner/applications")).andExpect(status().isCreated());
	}

	@Test
	void anIneligibleApplicationIsShownNotReplaced() throws Exception {
		String id = start();
		jdbc.update("UPDATE partner_application SET status = 'INELIGIBLE' WHERE public_id = ?", id);
		call(post("/v1/partner/applications")).andExpect(status().isOk())
			.andExpect(jsonPath("$.application_id").value(id))
			.andExpect(jsonPath("$.status").value("INELIGIBLE"))
			.andExpect(jsonPath("$.next_step").doesNotExist());
	}

	private String start() throws Exception {
		return client.body(call(post("/v1/partner/applications"))).get("application_id").asString();
	}

	private ResultActions consent(String id, String ifMatch, String body) throws Exception {
		MockHttpServletRequestBuilder request = post("/v1/partner/applications/" + id + "/consents")
			.contentType(MediaType.APPLICATION_JSON)
			.content(body);
		if (ifMatch != null) {
			request.header("If-Match", ifMatch);
		}
		return call(request);
	}

	private ResultActions call(MockHttpServletRequestBuilder request) throws Exception {
		return mvc.perform(request.header("Authorization", "Bearer " + token));
	}
}
