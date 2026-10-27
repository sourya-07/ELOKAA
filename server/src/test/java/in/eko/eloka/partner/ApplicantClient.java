package in.eko.eloka.partner;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.util.concurrent.atomic.AtomicLong;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Test helper: signs a fresh applicant in through the real API (fake OTP 123456). */
public final class ApplicantClient {

	private static final AtomicLong MOBILES = new AtomicLong(9_200_000_000L);

	private final MockMvc mvc;
	private final JsonMapper json;

	public ApplicantClient(MockMvc mvc, JsonMapper json) {
		this.mvc = mvc;
		this.json = json;
	}

	/** @return a bearer access token for a new applicant with a unique mobile */
	public String signInNewApplicant() throws Exception {
		String mobile = Long.toString(MOBILES.incrementAndGet());
		JsonNode sent = body(mvc.perform(post("/v1/partner/otp").contentType(MediaType.APPLICATION_JSON)
			.content("{\"mobile\":\"" + mobile + "\"}")));
		JsonNode tokens = body(mvc.perform(post("/v1/partner/otp/verify").contentType(MediaType.APPLICATION_JSON)
			.content("{\"otp_ref\":\"" + sent.get("otp_ref").asString() + "\",\"otp\":\"123456\"}")));
		return tokens.get("access_token").asString();
	}

	public JsonNode body(ResultActions result) throws Exception {
		return json.readTree(result.andReturn().getResponse().getContentAsString());
	}
}
