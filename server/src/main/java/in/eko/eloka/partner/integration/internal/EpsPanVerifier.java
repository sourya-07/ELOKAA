package in.eko.eloka.partner.integration.internal;

import java.time.LocalDate;
import java.util.Map;
import in.eko.eloka.partner.integration.PanVerifier;
import in.eko.eloka.partner.integration.ProviderUnavailableException;
import org.springframework.http.HttpMethod;
import tools.jackson.databind.JsonNode;

/**
 * EPS PAN Lite ({@code POST /tools/kyc/pan-lite}, https://eps.eko.in/docs/pan-lite): name and date
 * of birth match flags, PAN status and Aadhaar seeding. A missing match flag means EPS could not
 * tell, which is a technical failure, never a pass.
 */
class EpsPanVerifier implements PanVerifier {

	private static final String NAME = "PAN verification";

	private final EpsClient eps;

	EpsPanVerifier(EpsClient eps) {
		this.eps = eps;
	}

	@Override
	public PanCheck verify(String pan, String name, LocalDate dob) {
		String clientRef = EpsClient.clientRef();
		JsonNode response = eps.call("pan-lite", HttpMethod.POST, "/tools/kyc/pan-lite",
				Map.of("pan_number", pan, "name", name, "dob", dob.toString(), "client_ref_id", clientRef));
		JsonNode data = response.path("data");
		String validity = data.path("status").asString("");
		String panStatus = data.path("pan_status").asString("");
		boolean seeded = "Y".equals(data.path("aadhaar_seeding_status").asString(""));
		if ("INVALID".equals(validity) || (!panStatus.isEmpty() && !panStatus.startsWith("E"))) {
			return new PanCheck(Result.INVALID_PAN, seeded, clientRef);
		}
		if (response.get("status").asInt() != 0 || !"VALID".equals(validity)) {
			throw new ProviderUnavailableException(NAME);
		}
		String nameMatch = data.path("name_match").asString("");
		String dobMatch = data.path("dob_match").asString("");
		if (!Map.of("Y", 1, "N", 0).containsKey(nameMatch) || !Map.of("Y", 1, "N", 0).containsKey(dobMatch)) {
			throw new ProviderUnavailableException(NAME);
		}
		if ("N".equals(nameMatch)) {
			return new PanCheck(Result.NAME_MISMATCH, seeded, clientRef);
		}
		if ("N".equals(dobMatch)) {
			return new PanCheck(Result.DOB_MISMATCH, seeded, clientRef);
		}
		return new PanCheck(Result.MATCH, seeded, clientRef);
	}
}
