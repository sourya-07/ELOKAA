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
import in.eko.eloka.partner.integration.DigiLockerProvider;
import in.eko.eloka.partner.integration.ProviderUnavailableException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/**
 * The EPS DigiLocker adapter against a mock HTTP server, with the documented answers
 * (eps.eko.in/docs/digilocker-get-document). Not yet run against EPS UAT (Q-18, Q-22).
 */
class EpsDigiLockerTest {

	private static final String BASE = "https://staging.eko.in/ekoapi/v3";
	private static final String FETCH = BASE + "/tools/kyc/digilocker/document";
	private static final IntegrationProperties.Eps CONFIG = new IntegrationProperties.Eps(BASE, "dev-key", "access-key",
			"9000000000", null, null, null);

	private MockRestServiceServer server;
	private EpsDigiLocker digiLocker;

	@BeforeEach
	void setUp() {
		RestClient.Builder builder = RestClient.builder().baseUrl(BASE);
		server = MockRestServiceServer.bindTo(builder).build();
		digiLocker = new EpsDigiLocker(new EpsClient(builder.build(), CONFIG,
				Clock.fixed(Instant.parse("2026-10-28T10:00:00Z"), ZoneOffset.UTC)));
	}

	@Test
	void startAsksForAadhaarAndReturnsTheHttpsUrl() {
		server.expect(requestTo(BASE + "/tools/kyc/digilocker"))
			.andExpect(jsonPath("$.document_requested[0]").value("AADHAAR"))
			.andExpect(jsonPath("$.redirect_url").value("https://example.test/apply/identity?state=dls_x"))
			.andRespond(json("""
					{"status":0,"data":{"reference_id":7483920,"url":"https://digilocker.gov.in/auth?session_id=abc"}}"""));
		DigiLockerProvider.Session session = digiLocker.start("DLREF", "https://example.test/apply/identity?state=dls_x");
		assertThat(session.referenceId()).isEqualTo("7483920");
		assertThat(session.url()).startsWith("https://digilocker.gov.in/");
	}

	@Test
	void readsTheAadhaarDocument() {
		server.expect(requestTo(FETCH)).andExpect(jsonPath("$.verification_id").value("DLREF"))
			.andRespond(json("""
					{"status":0,"data":{"verification_status":"SUCCESS","documents":[{"document_type":"AADHAAR",
					"name":"Vikram Singh","dob":"12-11-1987","masked_aadhaar":"XXXX-XXXX-2345","address":{"house":"15B",
					"street":"Nehru Street","village_or_city":"Chennai","district":"Chennai","state":"Tamil Nadu",
					"pincode":"600001"}}]}}"""));
		DigiLockerProvider.Fetch fetch = digiLocker.fetch("DLREF", "7483920");
		assertThat(fetch).isInstanceOfSatisfying(DigiLockerProvider.Fetch.Done.class, done -> {
			assertThat(done.identity().aadhaarLast4()).isEqualTo("2345");
			assertThat(done.identity().dob()).isEqualTo(LocalDate.of(1987, 11, 12));
			assertThat(done.identity().address().pincode()).isEqualTo("600001");
		});
	}

	@Test
	void aFinishedTripWithoutAnAadhaarIsARealAnswer() {
		server.expect(requestTo(FETCH)).andRespond(json("""
				{"status":0,"data":{"verification_status":"SUCCESS","documents":[]}}"""));
		assertThat(digiLocker.fetch("DLREF", "1")).isEqualTo(new DigiLockerProvider.Fetch.Failed("NO_AADHAAR_DATA"));
	}

	@Test
	void pendingInvalidAndUnknownAnswers() {
		server.expect(requestTo(FETCH)).andRespond(json("""
				{"status":1,"response_status_id":327,"message":"Verification pending."}"""));
		server.expect(requestTo(FETCH)).andRespond(json("""
				{"status":1,"response_status_id":463,"message":"Invalid reference ID."}"""));
		server.expect(requestTo(FETCH)).andRespond(json("""
				{"status":1,"response_status_id":999,"message":"Something else"}"""));
		assertThat(digiLocker.fetch("DLREF", "1")).isInstanceOf(DigiLockerProvider.Fetch.Pending.class);
		assertThat(digiLocker.fetch("DLREF", "1")).isEqualTo(new DigiLockerProvider.Fetch.Failed("INVALID_REFERENCE"));
		// unknown codes are not a result: the caller treats them as an outage (nothing used up)
		assertThatThrownBy(() -> digiLocker.fetch("DLREF", "1")).isInstanceOf(ProviderUnavailableException.class);
	}

	private static org.springframework.test.web.client.ResponseCreator json(String body) {
		return withSuccess(body, MediaType.APPLICATION_JSON);
	}
}
