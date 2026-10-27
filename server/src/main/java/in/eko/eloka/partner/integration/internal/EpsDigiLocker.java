package in.eko.eloka.partner.integration.internal;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import in.eko.eloka.partner.integration.DigiLockerProvider;
import in.eko.eloka.partner.integration.ProviderUnavailableException;
import org.springframework.http.HttpMethod;
import tools.jackson.databind.JsonNode;

/**
 * EPS DigiLocker: Create DigiLocker URL ({@code POST /tools/kyc/digilocker}) and Get DigiLocker
 * Document ({@code POST /tools/kyc/digilocker/document}), https://eps.eko.in/docs/digilocker-create-url.
 * Our verification id is sent as {@code client_ref_id} when creating and as
 * {@code verification_id} when fetching (the create response documents none; untested against
 * EPS UAT, docs/OPEN_QUESTIONS.md Q-18). The Aadhaar photo in the answer is ignored, never stored.
 */
class EpsDigiLocker implements DigiLockerProvider {

	private static final String NAME = "DigiLocker";
	private static final DateTimeFormatter DOB = DateTimeFormatter.ofPattern("dd-MM-yyyy");
	/** EPS response_status_id: consent not completed yet. */
	private static final int PENDING = 327;
	/** EPS response_status_id: unknown reference. */
	private static final int INVALID_REFERENCE = 463;

	private final EpsClient eps;

	EpsDigiLocker(EpsClient eps) {
		this.eps = eps;
	}

	@Override
	public Session start(String verificationId, String returnUrl) {
		JsonNode response = eps.call("digilocker-create-url", HttpMethod.POST, "/tools/kyc/digilocker",
				Map.of("document_requested", List.of("AADHAAR"), "redirect_url", returnUrl, "client_ref_id",
						verificationId));
		JsonNode data = response.path("data");
		String reference = data.path("reference_id").asString("");
		String url = data.path("url").asString("");
		if (response.get("status").asInt() != 0 || reference.isBlank() || !url.startsWith("https://")) {
			throw new ProviderUnavailableException(NAME);
		}
		return new Session(reference, url);
	}

	@Override
	public Fetch fetch(String verificationId, String referenceId) {
		JsonNode response = eps.call("digilocker-get-document", HttpMethod.POST, "/tools/kyc/digilocker/document",
				Map.of("document_type", List.of("AADHAAR"), "verification_id", verificationId, "reference_id",
						referenceId, "client_ref_id", EpsClient.clientRef()));
		if (response.get("status").asInt() != 0) {
			return switch (response.path("response_status_id").asInt(-1)) {
				case PENDING -> new Fetch.Pending();
				case INVALID_REFERENCE -> new Fetch.Failed("INVALID_REFERENCE");
				default -> throw new ProviderUnavailableException(NAME);
			};
		}
		JsonNode data = response.path("data");
		if (!"SUCCESS".equals(data.path("verification_status").asString(""))) {
			return new Fetch.Pending();
		}
		for (JsonNode document : data.path("documents")) {
			if ("AADHAAR".equals(document.path("document_type").asString(""))) {
				return new Fetch.Done(identity(document));
			}
		}
		throw new ProviderUnavailableException(NAME);
	}

	private static Identity identity(JsonNode document) {
		String masked = document.path("masked_aadhaar").asString("").replaceAll("\\D", "");
		String name = document.path("name").asString("").trim();
		if (masked.length() < 4 || name.isEmpty()) {
			throw new ProviderUnavailableException(NAME);
		}
		LocalDate dob;
		try {
			dob = LocalDate.parse(document.path("dob").asString(""), DOB);
		}
		catch (DateTimeParseException ex) {
			throw new ProviderUnavailableException(NAME);
		}
		JsonNode address = document.path("address");
		return new Identity(name, dob, masked.substring(masked.length() - 4),
				new Address(text(address, "house"), text(address, "street"), text(address, "village_or_city"),
						text(address, "district"), text(address, "state"), text(address, "pincode")));
	}

	private static String text(JsonNode node, String field) {
		String value = node.path(field).asString("").trim();
		return value.isEmpty() ? null : value;
	}
}
