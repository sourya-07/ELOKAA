package in.eko.eloka.partner.integration.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/** The OpenStreetMap address lookup against a mock server, with the answer Nominatim documents for /reverse. */
class NominatimGeocoderTest {

	private static final GeocoderProperties CONFIG = new GeocoderProperties(GeocoderProperties.Provider.NOMINATIM, null, null,
			"ops@example.com", null);

	private MockRestServiceServer server;
	private NominatimGeocoder geocoder;

	@BeforeEach
	void setUp() {
		RestClient.Builder builder = RestClient.builder();
		server = MockRestServiceServer.bindTo(builder).build();
		geocoder = new NominatimGeocoder(CONFIG, builder);
	}

	@Test
	void asksForTheAddressOfThePointAndIdentifiesItself() {
		server.expect(requestTo(org.hamcrest.Matchers.startsWith("https://nominatim.openstreetmap.org/reverse")))
			.andExpect(queryParam("format", "jsonv2"))
			.andExpect(queryParam("lat", "19.059600"))
			.andExpect(queryParam("lon", "72.829500"))
			.andExpect(queryParam("email", "ops@example.com"))
			.andExpect(header("User-Agent", "ELOKA-partner-onboarding/1.0"))
			.andRespond(withSuccess("""
					{"place_id":1,"display_name":"Bandra West, Mumbai, Maharashtra, 400050, India"}""", MediaType.APPLICATION_JSON));
		assertThat(geocoder.addressOf(19.0596, 72.8295)).contains("Bandra West, Mumbai, Maharashtra, 400050, India");
		server.verify();
	}

	@Test
	void anUnknownPointOrAFailureGivesNoAddress() {
		server.expect(requestTo(org.hamcrest.Matchers.startsWith("https://nominatim.openstreetmap.org/reverse")))
			.andRespond(withSuccess("""
					{"error":"Unable to geocode"}""", MediaType.APPLICATION_JSON));
		server.expect(requestTo(org.hamcrest.Matchers.startsWith("https://nominatim.openstreetmap.org/reverse")))
			.andRespond(withServerError());
		assertThat(geocoder.addressOf(0.5, 0.5)).isEmpty();
		assertThat(geocoder.addressOf(19.0, 72.8)).isEmpty();
	}

	@Test
	void onlyOverHttps() {
		assertThatThrownBy(() -> new NominatimGeocoder(new GeocoderProperties(GeocoderProperties.Provider.NOMINATIM,
				"http://nominatim.example", null, null, null))).isInstanceOf(IllegalStateException.class);
	}
}
