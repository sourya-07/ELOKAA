package in.eko.eloka.partner.onboarding.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import javax.imageio.ImageIO;
import in.eko.eloka.partner.ApplicantClient;
import in.eko.eloka.partner.TestcontainersConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * The applicant journey over HTTP with the fake providers (answers chosen by PAN digits, see
 * FakeIdentityProviders) and real MySQL: both paths, the credit rule, and upload checks.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class JourneyIntegrationTest {

	private static final String VERSION = "2026-10-27";

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
	void start() throws Exception {
		client = new ApplicantClient(mvc, json);
		token = client.signInNewApplicant();
		track(call(post("/v1/partner/applications")).andExpect(status().isCreated()));
	}

	@Test
	void digiLockerJourneyFetchesIdentityAndAddressThenSubmits() throws Exception {
		verifyEmail();
		consent("DATA_PROCESSING");
		call(post(app("/digilocker-sessions")).header("If-Match", etag())).andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("CONSENT_REQUIRED"));
		consent("DIGILOCKER");
		String state = startDigiLocker();
		assertThat(state).startsWith("dls_");

		track(complete(state).andExpect(status().isOk())
			.andExpect(stepSummary("AADHAAR", "aadhaar_masked").value("XXXX-XXXX-1234"))
			.andExpect(stepSummary("AADHAAR", "address_from_digilocker").value(true))
			.andExpect(stepSummary("HOME_ADDRESS", "source").value("DIGILOCKER"))
			.andExpect(jsonPath("$.next_step").value("PAN")));
		complete(state).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("DIGILOCKER_ALREADY_USED"));
		complete("dls_forged").andExpect(status().isUnprocessableContent());

		// Name and date of birth come from DigiLocker: only the PAN is sent
		track(pan(newPan("4321")).andExpect(status().isOk()).andExpect(jsonPath("$.next_step").value("CREDIT_CHECK"))
			.andExpect(stepSummary("PAN", "pan_masked").exists()));
		creditCheck().andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CONSENT_REQUIRED"));
		consent("CREDIT_CHECK");
		track(creditCheck().andExpect(status().isOk()).andExpect(jsonPath("$.next_step").value("ALTERNATE_ID"))
			.andExpect(stepSummary("CREDIT_CHECK", "outcome").value("ELIGIBLE"))
			.andExpect(jsonPath("$.steps[?(@.step_key=='CREDIT_CHECK')].summary.score").doesNotExist()));
		track(alternateId("VOTER_ID", in.eko.eloka.partner.JourneyDriver.newEpic("4567"))
			.andExpect(status().isOk())
			.andExpect(stepSummary("ALTERNATE_ID", "type").value("VOTER_ID"))
			.andExpect(jsonPath("$.next_step").value("PHOTOS")));
		finishDocumentsAndSubmit();
		String details = jdbc.queryForObject("SELECT details FROM audit_event WHERE action = 'CREDIT_ASSESSED' "
				+ "AND target_id = ?", String.class, id);
		assertThat(details).contains("ELIGIBLE").doesNotContain("score").doesNotContain("742");
	}

	@Test
	void theSameDigiLockerIdentityAgainKeepsThePanAndTheCreditCheck() throws Exception {
		reachPhotos();
		consent("DIGILOCKER");
		String again = startDigiLocker();
		call(get(app(""))).andExpect(jsonPath("$.steps[?(@.step_key=='AADHAAR')].status").value("COMPLETED"));
		track(complete(again).andExpect(status().isOk())
			.andExpect(jsonPath("$.steps[?(@.step_key=='PAN')].status").value("COMPLETED"))
			.andExpect(jsonPath("$.steps[?(@.step_key=='ALTERNATE_ID')].status").value("COMPLETED"))
			.andExpect(jsonPath("$.steps[?(@.step_key=='CREDIT_CHECK')].status").value("COMPLETED"))
			.andExpect(jsonPath("$.next_step").value("PHOTOS")));
	}

	@Test
	void startingAgainEndsTheEarlierTripAndADigiLockerAddressIsNotRetyped() throws Exception {
		verifyEmail();
		consent("DATA_PROCESSING");
		consent("DIGILOCKER");
		String first = startDigiLocker();
		String second = startDigiLocker();
		complete(first).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("DIGILOCKER_ALREADY_USED"));
		track(complete(second).andExpect(status().isOk()));
		call(put(app("/home-address")).header("If-Match", etag()).contentType(MediaType.APPLICATION_JSON)
			.content("{\"line1\":\"Flat 12\",\"city\":\"Pune\",\"state\":\"Maharashtra\",\"pincode\":\"411001\"}"))
			.andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ADDRESS_FROM_DIGILOCKER"));
		// The retired step-by-step routes are gone
		call(put(app("/method")).header("If-Match", etag()).contentType(MediaType.APPLICATION_JSON)
			.content("{\"method\":\"MANUAL\"}")).andExpect(result -> assertThat(result.getResponse().getStatus()).isIn(404, 405));
	}

	@Test
	void aScoreBelow700ClosesTheApplicationAndBlocksThatPan() throws Exception {
		String lowPan = newPan("6990");
		reachCreditCheck(lowPan);
		track(creditCheck().andExpect(status().isOk()).andExpect(jsonPath("$.status").value("INELIGIBLE"))
			.andExpect(jsonPath("$.next_step").doesNotExist()));
		consentCall("CREDIT_CHECK").andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("APPLICATION_NOT_EDITABLE"));
		call(post("/v1/partner/applications")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("INELIGIBLE"));

		// The same PAN cannot be tried again by someone else
		start();
		toPan();
		pan(lowPan).andExpect(status().isOk())
			.andExpect(jsonPath("$.steps[?(@.step_key=='PAN')].last_error_code").value("PAN_ALREADY_USED"));
	}

	@Test
	void exactly700IsEligible() throws Exception {
		reachCreditCheck(newPan("7000"));
		creditCheck().andExpect(status().isOk()).andExpect(stepSummary("CREDIT_CHECK", "outcome").value("ELIGIBLE"));
	}

	@Test
	void bureauFailuresNeverPassAndBlockAfterThreeTries() throws Exception {
		reachCreditCheck(newPan("9998"));
		for (int attempt = 1; attempt <= 3; attempt++) {
			track(call(post(app("/credit-assessments")).header("If-Match", etag())
				.header("Idempotency-Key", UUID.randomUUID().toString())).andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("IN_PROGRESS"))
				.andExpect(jsonPath("$.steps[?(@.step_key=='CREDIT_CHECK')].last_error_code").value("BUREAU_UNAVAILABLE"))
				.andExpect(jsonPath("$.steps[?(@.step_key=='CREDIT_CHECK')].status").value(attempt < 3 ? "FAILED" : "BLOCKED")));
		}
		creditCheck().andExpect(status().isLocked());
		call(post(app("/submit")).header("If-Match", etag()).header("Idempotency-Key", UUID.randomUUID().toString()))
			.andExpect(status().isConflict());
	}

	@Test
	void anOutOfRangeScoreIsNotAPass() throws Exception {
		reachCreditCheck(newPan("9997"));
		creditCheck().andExpect(status().isOk())
			.andExpect(jsonPath("$.steps[?(@.step_key=='CREDIT_CHECK')].last_error_code").value("INVALID_SCORE"));
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM credit_assessment c JOIN partner_application a "
				+ "ON a.id = c.application_id WHERE a.public_id = ? AND c.score IS NULL", Integer.class, id)).isEqualTo(1);
	}

	@Test
	void noCreditHistoryIsNotAPass() throws Exception {
		reachCreditCheck(newPan("0001"));
		creditCheck().andExpect(status().isOk())
			.andExpect(jsonPath("$.steps[?(@.step_key=='CREDIT_CHECK')].last_error_code").value("NO_CREDIT_HISTORY"))
			.andExpect(jsonPath("$.steps[?(@.step_key=='CREDIT_CHECK')].status").value("FAILED"));
	}

	@Test
	void aPanThatDoesNotMatchBlocksTheStepAfterThreeTries() throws Exception {
		toPan();
		for (int attempt = 1; attempt <= 3; attempt++) {
			track(pan(newPan("1111")).andExpect(status().isOk())
				.andExpect(jsonPath("$.steps[?(@.step_key=='PAN')].last_error_code").value("PAN_NAME_MISMATCH")));
		}
		pan(newPan("4321")).andExpect(status().isLocked());
		pan("ABCDE1234F").andExpect(status().isLocked());
	}

	@Test
	void panInputIsValidatedAndNeedsDigiLockerFirst() throws Exception {
		verifyEmail();
		consent("DATA_PROCESSING");
		pan(newPan("4321")).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("PREREQUISITE_INCOMPLETE"));
		consent("DIGILOCKER");
		track(complete(startDigiLocker()));
		pan("ABCCE1234F").andExpect(status().isUnprocessableContent())
			.andExpect(jsonPath("$.invalid_params.pan").exists()); // a company PAN
		pan("not-a-pan").andExpect(status().isUnprocessableContent());
	}

	@Test
	void theSnapshotSaysWhatStillStopsSubmissionAndOldTimelineRowsStillLoad() throws Exception {
		track(call(get(app(""))).andExpect(status().isOk())
			.andExpect(jsonPath("$.available_actions[?(@=='SUBMIT')]").isEmpty())
			.andExpect(jsonPath("$.submit_blockers[?(@.step_key=='CONSENT')].code").value("STEP_NOT_DONE"))
			.andExpect(jsonPath("$.submit_blockers[?(@.group=='PASSPORT_PHOTO')].code").value("DOCUMENTS_MISSING"))
			.andExpect(jsonPath("$.requirements[?(@.group=='VOTER_ID_OR_DRIVING_LICENCE')].rule").value("ONE_OF")));

		// A step a later journey version retired must not break the timeline of older applications
		jdbc.update("INSERT INTO application_event (id, application_id, event_type, step_key, actor, occurred_at, version, "
				+ "created_at, updated_at) SELECT UUID_TO_BIN(UUID()), id, 'STEP_COMPLETED', 'RETIRED_STEP', 'APPLICANT', "
				+ "NOW(6), 0, NOW(6), NOW(6) FROM partner_application WHERE public_id = ?", id);
		call(get(app("/timeline"))).andExpect(status().isOk())
			.andExpect(jsonPath("$[?(@.step_key=='RETIRED_STEP')]").isNotEmpty());
	}

	@Test
	void theLivePhotoNeedsARealLocation() throws Exception {
		reachPhotos();
		byte[] photo = jpeg(600, 600, null);
		call(multipart(app("/documents")).file(file("file", "me.jpg", photo)).param("kind", "LIVE_PHOTO"))
			.andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.code").value("LOCATION_REQUIRED"));
		call(multipart(app("/documents")).file(file("file", "me.jpg", photo)).param("kind", "LIVE_PHOTO")
			.param("latitude", "0").param("longitude", "0").param("accuracy_m", "10"))
			.andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.code").value("LOCATION_INVALID"));
		call(multipart(app("/documents")).file(file("file", "me.jpg", photo)).param("kind", "LIVE_PHOTO")
			.param("latitude", "19.06").param("longitude", "72.83").param("accuracy_m", "5000"))
			.andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.code").value("LOCATION_TOO_INACCURATE"));
		call(multipart(app("/documents")).file(file("file", "me.jpg", photo)).param("kind", "LIVE_PHOTO")
			.param("latitude", "51.5").param("longitude", "-0.12").param("accuracy_m", "250")
			.param("captured_at", java.time.Instant.now().toString()))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.location.accuracy_m").value(250))
			.andExpect(jsonPath("$.location.flags").value("LOW_ACCURACY,OUTSIDE_INDIA"))
			.andExpect(jsonPath("$.location.latitude").doesNotExist());
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM photo_location l JOIN partner_application a "
				+ "ON a.id = l.application_id WHERE a.public_id = ?", Integer.class, id)).isEqualTo(1);
		// the rejected tries stored nothing
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM application_document d JOIN partner_application a "
				+ "ON a.id = d.application_id WHERE a.public_id = ? AND d.kind = 'LIVE_PHOTO'", Integer.class, id)).isEqualTo(1);
	}

	@Test
	void uploadsAreCheckedByTheirContent() throws Exception {
		call(multipart(app("/documents")).file(file("file", "me.jpg", jpeg(600, 600, null))).param("kind", "LIVE_PHOTO"))
			.andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("PREREQUISITE_INCOMPLETE"));
		reachPhotos();
		upload("LIVE_PHOTO", "me.png", png()).andExpect(status().isUnprocessableContent());
		upload("LIVE_PHOTO", "me.jpg", jpeg(120, 120, null)).andExpect(status().isUnprocessableContent());
		upload("LIVE_PHOTO", "me.jpg", "MZ not really a picture".getBytes()).andExpect(status().isUnprocessableContent());
		upload("LIVE_PHOTO", "me.pdf", pdf("")).andExpect(status().isUnprocessableContent());
		upload("GRADUATION_CERTIFICATE", "degree.pdf",
				pdf("X5O!P%@AP[4\\PZX54(P^)7CC)7}$EICAR-STANDARD-ANTIVIRUS-TEST-FILE!$H+H*"))
			.andExpect(status().isUnprocessableContent());

		// Anything appended after the picture is gone once stored
		byte[] polyglot = jpeg(600, 600, "<script>alert(1)</script>");
		String documentId = client.body(upload("LIVE_PHOTO", "../../etc/passwd.jpg", polyglot).andExpect(status().isCreated())
			.andExpect(jsonPath("$.status").value("MANUAL_REVIEW_REQUIRED"))
			.andExpect(jsonPath("$.file_name").value("______etc_passwd.jpg"))).get("document_id").asString();
		byte[] stored = call(get(app("/documents/" + documentId + "/content"))).andExpect(status().isOk())
			.andReturn().getResponse().getContentAsByteArray();
		assertThat(new String(stored, StandardCharsets.ISO_8859_1)).doesNotContain("<script>");

		refresh();
		call(post(app("/photos/submit")).header("If-Match", etag())).andExpect(status().isConflict())
			.andExpect(jsonPath("$.missing_documents[0]").value("PASSPORT_PHOTO"));

		// Another applicant cannot read it
		String otherToken = client.signInNewApplicant();
		mvc.perform(get(app("/documents/" + documentId + "/content")).header("Authorization", "Bearer " + otherToken))
			.andExpect(status().isNotFound());

		// A replaced photo is no longer the applicant's to fetch
		upload("LIVE_PHOTO", "again.jpg", jpeg(600, 600, null)).andExpect(status().isCreated());
		call(get(app("/documents/" + documentId + "/content"))).andExpect(status().isNotFound());
	}

	@Test
	void theIdImagesMustBeOfTheVerifiedDocument() throws Exception {
		reachPhotos(); // the voter ID was verified on the way
		uploadPhotosAndEducation();
		upload("DRIVING_LICENCE_FRONT", "dl.jpg", jpeg(800, 500, null)).andExpect(status().isCreated());
		upload("DRIVING_LICENCE_BACK", "dl.jpg", jpeg(800, 500, null)).andExpect(status().isCreated());
		refresh();
		// a driving licence nobody checked does not count
		submitId().andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("DOCUMENTS_MISSING"))
			.andExpect(jsonPath("$.missing_documents[0]").value("VOTER_ID_FRONT"));
		upload("VOTER_ID_FRONT", "voter.jpg", jpeg(800, 500, null)).andExpect(status().isCreated());
		upload("VOTER_ID_BACK", "voter.jpg", jpeg(800, 500, null)).andExpect(status().isCreated());
		refresh();
		track(submitId().andExpect(status().isOk()).andExpect(stepSummary("ID_DOCUMENT", "voter_id").value(true))
			.andExpect(jsonPath("$.next_step").value("DECLARATION")));
	}

	// ---- journey helpers ------------------------------------------------------------------------

	/** Contact details, consents and DigiLocker done: the PAN is next. */
	private void toPan() throws Exception {
		verifyEmail();
		consent("DATA_PROCESSING");
		consent("DIGILOCKER");
		track(complete(startDigiLocker()).andExpect(status().isOk()).andExpect(jsonPath("$.next_step").value("PAN")));
	}

	private String startDigiLocker() throws Exception {
		String url = client.body(call(post(app("/digilocker-sessions")).header("If-Match", etag())).andExpect(status().isOk()))
			.get("url").asString();
		refresh();
		return url.substring(url.indexOf("state=") + 6);
	}

	private ResultActions complete(String state) throws Exception {
		return call(post(app("/digilocker-sessions/complete")).contentType(MediaType.APPLICATION_JSON)
			.content("{\"state\":\"" + state + "\"}"));
	}

	private void reachCreditCheck(String pan) throws Exception {
		toPan();
		track(pan(pan).andExpect(status().isOk()).andExpect(jsonPath("$.next_step").value("CREDIT_CHECK")));
		consent("CREDIT_CHECK");
	}

	private void reachPhotos() throws Exception {
		reachCreditCheck(newPan("4321"));
		track(creditCheck().andExpect(status().isOk()).andExpect(jsonPath("$.next_step").value("ALTERNATE_ID")));
		track(alternateId("VOTER_ID", in.eko.eloka.partner.JourneyDriver.newEpic("4567")).andExpect(status().isOk())
			.andExpect(jsonPath("$.next_step").value("PHOTOS")));
	}

	private ResultActions alternateId(String type, String number) throws Exception {
		return call(post(app("/alternate-id-verifications")).header("If-Match", etag())
			.header("Idempotency-Key", UUID.randomUUID().toString())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"type\":\"" + type + "\",\"number\":\"" + number + "\"}"));
	}

	private void uploadPhotosAndEducation() throws Exception {
		upload("LIVE_PHOTO", "me.jpg", jpeg(600, 600, null)).andExpect(status().isCreated());
		upload("PASSPORT_PHOTO", "passport.jpg", jpeg(350, 450, null)).andExpect(status().isCreated());
		refresh();
		track(call(post(app("/photos/submit")).header("If-Match", etag())).andExpect(status().isOk())
			.andExpect(jsonPath("$.next_step").value("EDUCATION")));
		upload("GRADUATION_CERTIFICATE", "degree.pdf", pdf("")).andExpect(status().isCreated());
		upload("COMPUTER_CERTIFICATE", "ccc.jpg", jpeg(800, 600, null)).andExpect(status().isCreated());
		refresh();
		track(call(post(app("/education/submit")).header("If-Match", etag())).andExpect(status().isOk())
			.andExpect(stepSummary("EDUCATION", "computer_certificates").value(1)));
	}

	private void finishDocumentsAndSubmit() throws Exception {
		uploadPhotosAndEducation();
		upload("VOTER_ID_FRONT", "voter.jpg", jpeg(800, 500, null)).andExpect(status().isCreated());
		upload("VOTER_ID_BACK", "voter.jpg", jpeg(800, 500, null)).andExpect(status().isCreated());
		refresh();
		track(submitId().andExpect(status().isOk()));
		track(call(post(app("/declaration")).header("If-Match", etag()).contentType(MediaType.APPLICATION_JSON)
			.content("{\"version\":\"" + VERSION + "\",\"accepted\":true}")).andExpect(status().isOk()));
		refresh();
		call(get(app(""))).andExpect(jsonPath("$.submit_blockers").isEmpty())
			.andExpect(jsonPath("$.available_actions[?(@=='SUBMIT')]").isNotEmpty());
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_event WHERE action = 'DECLARATION_ACCEPTED' "
				+ "AND target_id = ?", Integer.class, id)).isEqualTo(1);
		call(post(app("/submit")).header("If-Match", etag()).header("Idempotency-Key", UUID.randomUUID().toString()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("SUBMITTED"))
			.andExpect(jsonPath("$.steps[?(@.status!='LOCKED')]").isEmpty());
	}

	// ---- request helpers ------------------------------------------------------------------------

	private ResultActions pan(String pan) throws Exception {
		return call(post(app("/pan-verifications")).header("If-Match", etag())
			.header("Idempotency-Key", UUID.randomUUID().toString())
			.contentType(MediaType.APPLICATION_JSON)
			.content(json.writeValueAsString(java.util.Map.of("pan", pan))));
	}

	private ResultActions creditCheck() throws Exception {
		return call(post(app("/credit-assessments")).header("If-Match", etag())
			.header("Idempotency-Key", UUID.randomUUID().toString()));
	}

	/** Contact details: a code to a fresh address, then the fixed test code. */
	private void verifyEmail() throws Exception {
		String ref = client.body(call(post(app("/email-verifications")).contentType(MediaType.APPLICATION_JSON)
			.content("{\"email\":\"" + id.toLowerCase() + "@example.com\"}")).andExpect(status().isAccepted()))
			.get("challenge_ref").asString();
		track(call(post(app("/email-verifications/verify")).header("If-Match", etag())
			.header("Idempotency-Key", UUID.randomUUID().toString())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"challenge_ref\":\"" + ref + "\",\"otp\":\"123456\"}")).andExpect(status().isOk()));
	}

	private void consent(String purpose) throws Exception {
		track(consentCall(purpose).andExpect(status().isOk()));
	}

	private ResultActions consentCall(String purpose) throws Exception {
		return call(post(app("/consents")).header("If-Match", etag()).contentType(MediaType.APPLICATION_JSON)
			.content("{\"purpose\":\"" + purpose + "\",\"version\":\"" + VERSION + "\"}"));
	}

	private ResultActions upload(String kind, String name, byte[] bytes) throws Exception {
		MockMultipartHttpServletRequestBuilder request = multipart(app("/documents")).file(file("file", name, bytes));
		request.param("kind", kind);
		if (kind.equals("LIVE_PHOTO")) {
			in.eko.eloka.partner.JourneyDriver.withLocation(request);
		}
		return call(request);
	}

	private ResultActions submitId() throws Exception {
		return call(post(app("/id-document/submit")).header("If-Match", etag()));
	}

	private void refresh() throws Exception {
		track(call(get(app(""))).andExpect(status().isOk()));
	}

	private void track(ResultActions result) throws Exception {
		JsonNode snapshot = client.body(result);
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

	private ResultActions call(MockMultipartHttpServletRequestBuilder request) throws Exception {
		return mvc.perform(request.header("Authorization", "Bearer " + token));
	}

	private static org.springframework.test.web.servlet.result.JsonPathResultMatchers stepSummary(String step,
			String field) {
		return jsonPath("$.steps[?(@.step_key=='" + step + "')].summary." + field);
	}

	/** A personal PAN whose digits pick the fake providers' answer; the letters keep it unique. */
	private static String newPan(String digits) {
		ThreadLocalRandom random = ThreadLocalRandom.current();
		StringBuilder pan = new StringBuilder();
		for (int i = 0; i < 3; i++) {
			pan.append((char) ('A' + random.nextInt(26)));
		}
		pan.append('P').append((char) ('A' + random.nextInt(26))).append(digits).append((char) ('A' + random.nextInt(26)));
		return pan.toString();
	}

	private static MockMultipartFile file(String part, String name, byte[] bytes) {
		return new MockMultipartFile(part, name, "application/octet-stream", bytes);
	}

	private static byte[] jpeg(int width, int height, String trailer) throws Exception {
		BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
		var graphics = image.createGraphics();
		graphics.setColor(Color.ORANGE);
		graphics.fillRect(0, 0, width, height);
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		ImageIO.write(image, "jpeg", out);
		if (trailer != null) {
			out.write(trailer.getBytes(StandardCharsets.UTF_8));
		}
		return out.toByteArray();
	}

	private static byte[] png() throws Exception {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		ImageIO.write(new BufferedImage(600, 600, BufferedImage.TYPE_INT_RGB), "png", out);
		return out.toByteArray();
	}

	private static byte[] pdf(String body) {
		return ("%PDF-1.4\n1 0 obj << >> endobj\n" + body + "\n%%EOF").getBytes(StandardCharsets.ISO_8859_1);
	}
}
