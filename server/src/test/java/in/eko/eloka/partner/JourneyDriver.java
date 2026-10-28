package in.eko.eloka.partner;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import javax.imageio.ImageIO;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Test helper: one applicant driven through the real API with the fake providers. Tracks the
 * application id and version so each call sends a current {@code If-Match}.
 */
public final class JourneyDriver {

	public static final String VERSION = "2026-10-27";

	private final MockMvc mvc;
	private final ApplicantClient client;
	private final String token;
	private String id;
	private long version;
	private String pan;

	public JourneyDriver(MockMvc mvc, JsonMapper json) throws Exception {
		this.mvc = mvc;
		this.client = new ApplicantClient(mvc, json);
		this.token = client.signInNewApplicant();
		track(call(post("/v1/partner/applications")).andExpect(status().isCreated()));
	}

	public String id() {
		return id;
	}

	public String pan() {
		return pan;
	}

	public String token() {
		return token;
	}

	/** Through DigiLocker, all the way to SUBMITTED (the address comes from DigiLocker). */
	public JourneyDriver submit() throws Exception {
		verifyEmail();
		consent("DATA_PROCESSING");
		consent("DIGILOCKER");
		String url = client.body(call(post(app("/digilocker-sessions")).header("If-Match", etag())).andExpect(status().isOk()))
			.get("url").asString();
		track(call(post(app("/digilocker-sessions/complete")).contentType(MediaType.APPLICATION_JSON)
			.content("{\"state\":\"" + url.substring(url.indexOf("state=") + 6) + "\"}")).andExpect(status().isOk()));
		pan = newPan("4321");
		track(call(post(app("/pan-verifications")).header("If-Match", etag())
			.header("Idempotency-Key", UUID.randomUUID().toString())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"pan\":\"" + pan + "\"}")).andExpect(status().isOk()));
		consent("CREDIT_CHECK");
		track(call(post(app("/credit-assessments")).header("If-Match", etag())
			.header("Idempotency-Key", UUID.randomUUID().toString())).andExpect(status().isOk()));
		upload("LIVE_PHOTO", jpeg(600, 600));
		upload("PASSPORT_PHOTO", jpeg(350, 450));
		submitStep("/photos/submit", null);
		upload("GRADUATION_CERTIFICATE", jpeg(800, 600));
		submitStep("/education/submit", null);
		upload("VOTER_ID_FRONT", jpeg(800, 500));
		upload("VOTER_ID_BACK", jpeg(800, 500));
		submitStep("/id-document/submit", "{\"voter_id_number\":\"ABC1234567\"}");
		return declareAndSubmit();
	}

	public JourneyDriver declareAndSubmit() throws Exception {
		submitStep("/declaration", "{\"version\":\"" + VERSION + "\",\"accepted\":true}");
		track(call(post(app("/submit")).header("If-Match", etag()).header("Idempotency-Key", UUID.randomUUID().toString()))
			.andExpect(status().isOk()));
		return this;
	}

	public ResultActions upload(String kind, byte[] bytes) throws Exception {
		return call(multipart(app("/documents")).file(file("file", bytes)).param("kind", kind));
	}

	public void submitStep(String path, String body) throws Exception {
		refresh();
		MockHttpServletRequestBuilder request = post(app(path)).header("If-Match", etag());
		if (body != null) {
			request.contentType(MediaType.APPLICATION_JSON).content(body);
		}
		track(call(request).andExpect(status().isOk()));
	}

	public JsonNode refresh() throws Exception {
		return track(call(get(app(""))).andExpect(status().isOk()));
	}

	public ResultActions call(MockHttpServletRequestBuilder request) throws Exception {
		return mvc.perform(request.header("Authorization", "Bearer " + token));
	}

	public ResultActions call(MockMultipartHttpServletRequestBuilder request) throws Exception {
		return mvc.perform(request.header("Authorization", "Bearer " + token));
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
		track(call(post(app("/consents")).header("If-Match", etag()).contentType(MediaType.APPLICATION_JSON)
			.content("{\"purpose\":\"" + purpose + "\",\"version\":\"" + VERSION + "\"}")).andExpect(status().isOk()));
	}

	private JsonNode track(ResultActions result) throws Exception {
		JsonNode snapshot = client.body(result);
		id = snapshot.get("application_id").asString();
		version = snapshot.get("version").asLong();
		return snapshot;
	}

	private String etag() {
		return "\"v" + version + "\"";
	}

	private String app(String path) {
		return "/v1/partner/applications/" + id + path;
	}

	/** A personal PAN whose digits pick the fake providers' answer; the letters keep it unique. */
	public static String newPan(String digits) {
		ThreadLocalRandom random = ThreadLocalRandom.current();
		StringBuilder value = new StringBuilder();
		for (int i = 0; i < 3; i++) {
			value.append((char) ('A' + random.nextInt(26)));
		}
		return value.append('P').append((char) ('A' + random.nextInt(26))).append(digits)
			.append((char) ('A' + random.nextInt(26))).toString();
	}

	public static byte[] jpeg(int width, int height) throws Exception {
		BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
		var graphics = image.createGraphics();
		graphics.setColor(Color.ORANGE);
		graphics.fillRect(0, 0, width, height);
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		ImageIO.write(image, "jpeg", out);
		return out.toByteArray();
	}

	static byte[] pdf() {
		return "%PDF-1.4\n%%EOF".getBytes(StandardCharsets.ISO_8859_1);
	}

	private static MockMultipartFile file(String part, byte[] bytes) {
		return new MockMultipartFile(part, part + ".jpg", "image/jpeg", bytes);
	}
}
