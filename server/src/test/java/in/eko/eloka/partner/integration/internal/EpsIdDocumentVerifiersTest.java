package in.eko.eloka.partner.integration.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import in.eko.eloka.partner.integration.IdDocumentCheck;
import in.eko.eloka.partner.integration.ProviderUnavailableException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.ResponseCreator;
import org.springframework.web.client.RestClient;

/** EPS Voter ID, Driving Licence and Name Match adapters with the documented answers. Not yet run on EPS UAT. */
class EpsIdDocumentVerifiersTest {

	private static final String BASE = "https://staging.eko.in/ekoapi/v3";
	private static final IntegrationProperties.Eps CONFIG = new IntegrationProperties.Eps(BASE, "dev-key", "access-key",
			"9000000000", null, null, null);

	private MockRestServiceServer server;
	private EpsClient eps;

	@BeforeEach
	void setUp() {
		RestClient.Builder builder = RestClient.builder().baseUrl(BASE);
		server = MockRestServiceServer.bindTo(builder).build();
		eps = new EpsClient(builder.build(), CONFIG, Clock.fixed(Instant.parse("2026-10-28T10:00:00Z"), ZoneOffset.UTC));
	}

	@Test
	void voterIdRecordOrNoRecord() {
		var voter = new EpsIdDocumentVerifiers.VoterId(eps);
		server.expect(requestTo(BASE + "/tools/kyc/voter-id")).andExpect(jsonPath("$.epic_number").value("ABC1234567"))
			.andRespond(json("""
					{"status":0,"data":{"name":"Rajesh Kumar","dob":"1994-08-29","epic_number":"ABC1234567"}}"""));
		server.expect(requestTo(BASE + "/tools/kyc/voter-id")).andRespond(json("""
				{"status":1,"response_status_id":-1,"message":"No record found for the provided EPIC number"}"""));
		server.expect(requestTo(BASE + "/tools/kyc/voter-id")).andRespond(json("""
				{"status":1,"response_status_id":-1,"message":"epic_number is required"}"""));

		assertThat(voter.verify("ABC1234567")).isInstanceOfSatisfying(IdDocumentCheck.Found.class, found -> {
			assertThat(found.name()).isEqualTo("Rajesh Kumar");
			assertThat(found.dob()).isEqualTo(LocalDate.of(1994, 8, 29));
			assertThat(found.active()).isTrue();
		});
		assertThat(voter.verify("ABC1234567")).isInstanceOf(IdDocumentCheck.NotFound.class);
		assertThatThrownBy(() -> voter.verify("ABC1234567")).isInstanceOf(ProviderUnavailableException.class);
	}

	@Test
	void drivingLicenceStatusAndAnswers() {
		var licence = new EpsIdDocumentVerifiers.DrivingLicence(eps);
		server.expect(requestTo(BASE + "/tools/kyc/driving-license")).andExpect(jsonPath("$.dob").value("1994-08-29"))
			.andRespond(json("""
					{"status":0,"data":{"dl_number":"MH0220190001234","dob":"1994-08-29","status":"Expired",
					"details_of_driving_licence":{"name":"Rajesh Kumar","status":"Expired"}}}"""));
		server.expect(requestTo(BASE + "/tools/kyc/driving-license")).andRespond(json("""
				{"status":1,"response_status_id":1,"message":"Date of birth does not match DL records"}"""));
		server.expect(requestTo(BASE + "/tools/kyc/driving-license")).andRespond(json("""
				{"status":1,"response_status_id":1,"message":"Source unavailable. Please try again."}"""));

		assertThat(licence.verify("MH0220190001234", LocalDate.of(1994, 8, 29)))
			.isInstanceOfSatisfying(IdDocumentCheck.Found.class, found -> assertThat(found.active()).isFalse());
		assertThat(licence.verify("MH0220190001234", LocalDate.of(1994, 8, 29))).isInstanceOf(IdDocumentCheck.DobMismatch.class);
		assertThatThrownBy(() -> licence.verify("MH0220190001234", LocalDate.of(1994, 8, 29)))
			.isInstanceOf(ProviderUnavailableException.class);
	}

	@Test
	void nameMatchScore() {
		var names = new EpsIdDocumentVerifiers.Names(eps);
		server.expect(requestTo(BASE + "/tools/kyc/name-match")).andExpect(jsonPath("$.name_1").value("S K Mishra"))
			.andRespond(json("""
					{"status":0,"data":{"score":0.92,"reason":"Names match with initials expanded"}}"""));
		server.expect(requestTo(BASE + "/tools/kyc/name-match")).andRespond(json("""
				{"status":1,"message":"name_1 and name_2 are required fields."}"""));
		assertThat(names.score("S K Mishra", "Satish Kumar Mishra")).isEqualTo(0.92);
		assertThatThrownBy(() -> names.score("", "")).isInstanceOf(ProviderUnavailableException.class);
	}

	private static ResponseCreator json(String body) {
		return withSuccess(body, MediaType.APPLICATION_JSON);
	}
}
