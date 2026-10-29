package in.eko.eloka.partner;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;

/** A whole application, then the logs are searched for the personal data that went through them. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@ExtendWith(OutputCaptureExtension.class)
class LogLeakIntegrationTest {

	@Autowired
	MockMvc mvc;

	@Autowired
	JsonMapper json;

	@Test
	void noPersonalDataOrTokensInTheLogs(CapturedOutput output) throws Exception {
		JourneyDriver applicant = new JourneyDriver(mvc, json).submit();
		String logs = output.getAll();
		assertThat(logs).isNotEmpty();
		assertThat(logs).doesNotContain(applicant.pan());
		assertThat(logs).doesNotContain(applicant.token());
		assertThat(logs).doesNotContain("ptn_rt_");
		assertThat(logs).doesNotContain("Ravi Kumar").doesNotContain("RAVI KUMAR");
		assertThat(logs).doesNotContain("ABC1234567"); // voter ID number
		assertThat(logs).doesNotContain("19.0596").doesNotContain("72.8295"); // the live photo's coordinates
		assertThat(logs).doesNotContainPattern("(?<!\\d)92\\d{8}(?!\\d)"); // the test mobiles (92xxxxxxxx)
	}
}
