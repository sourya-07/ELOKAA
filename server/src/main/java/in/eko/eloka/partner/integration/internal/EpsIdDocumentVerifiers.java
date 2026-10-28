package in.eko.eloka.partner.integration.internal;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import java.util.Map;
import in.eko.eloka.partner.integration.DrivingLicenceVerifier;
import in.eko.eloka.partner.integration.IdDocumentCheck;
import in.eko.eloka.partner.integration.NameMatcher;
import in.eko.eloka.partner.integration.ProviderUnavailableException;
import in.eko.eloka.partner.integration.VoterIdVerifier;
import org.springframework.http.HttpMethod;
import tools.jackson.databind.JsonNode;

/**
 * EPS record checks for the second identity document and the name comparison
 * (eps.eko.in/docs/voter-id, /driving-license, /name-match). EPS answers failures with
 * {@code status != 0} and a message; only the documented "no record" and "date of birth does not
 * match" answers are results, anything else is an outage (never a pass, never a used attempt).
 * Not yet run against EPS UAT (docs/OPEN_QUESTIONS.md Q-22).
 */
final class EpsIdDocumentVerifiers {

	private EpsIdDocumentVerifiers() {
	}

	static final class VoterId implements VoterIdVerifier {

		private static final String NAME = "Voter ID verification";

		private final EpsClient eps;

		VoterId(EpsClient eps) {
			this.eps = eps;
		}

		@Override
		public IdDocumentCheck verify(String epicNumber) {
			String clientRef = EpsClient.clientRef();
			JsonNode response = eps.call("voter-id", HttpMethod.POST, "/tools/kyc/voter-id",
					Map.of("epic_number", epicNumber, "client_ref_id", clientRef));
			if (response.get("status").asInt() != 0) {
				if (says(response, "no record")) {
					return new IdDocumentCheck.NotFound();
				}
				throw new ProviderUnavailableException(NAME);
			}
			JsonNode data = response.path("data");
			String name = data.path("name").asString("").trim();
			if (name.isEmpty()) {
				throw new ProviderUnavailableException(NAME);
			}
			return new IdDocumentCheck.Found(name, date(data.path("dob").asString("")), true, clientRef);
		}
	}

	static final class DrivingLicence implements DrivingLicenceVerifier {

		private static final String NAME = "Driving licence verification";

		private final EpsClient eps;

		DrivingLicence(EpsClient eps) {
			this.eps = eps;
		}

		@Override
		public IdDocumentCheck verify(String licenceNumber, LocalDate dob) {
			String clientRef = EpsClient.clientRef();
			JsonNode response = eps.call("driving-license", HttpMethod.POST, "/tools/kyc/driving-license",
					Map.of("dl_number", licenceNumber, "dob", dob.toString(), "client_ref_id", clientRef));
			if (response.get("status").asInt() != 0) {
				if (says(response, "no record")) {
					return new IdDocumentCheck.NotFound();
				}
				if (says(response, "date of birth does not match")) {
					return new IdDocumentCheck.DobMismatch();
				}
				throw new ProviderUnavailableException(NAME);
			}
			JsonNode data = response.path("data");
			String name = data.path("details_of_driving_licence").path("name").asString("").trim();
			if (name.isEmpty()) {
				throw new ProviderUnavailableException(NAME);
			}
			boolean active = "active".equalsIgnoreCase(data.path("status").asString(""));
			return new IdDocumentCheck.Found(name, date(data.path("dob").asString("")), active, clientRef);
		}
	}

	static final class Names implements NameMatcher {

		private final EpsClient eps;

		Names(EpsClient eps) {
			this.eps = eps;
		}

		@Override
		public double score(String name1, String name2) {
			JsonNode response = eps.call("name-match", HttpMethod.POST, "/tools/kyc/name-match",
					Map.of("name_1", name1, "name_2", name2, "client_ref_id", EpsClient.clientRef()));
			JsonNode score = response.path("data").path("score");
			if (response.get("status").asInt() != 0 || !score.isNumber()) {
				throw new ProviderUnavailableException("Name match");
			}
			return Math.max(0, Math.min(1, score.asDouble()));
		}
	}

	private static boolean says(JsonNode response, String words) {
		return response.path("message").asString("").toLowerCase(Locale.ROOT).contains(words);
	}

	/** {@code YYYY-MM-DD}, or null when missing or in another shape (then it is not compared). */
	private static LocalDate date(String value) {
		try {
			return value.isBlank() ? null : LocalDate.parse(value.trim());
		}
		catch (DateTimeParseException ex) {
			return null;
		}
	}
}
