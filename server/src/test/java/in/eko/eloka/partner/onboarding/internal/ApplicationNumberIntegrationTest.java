package in.eko.eloka.partner.onboarding.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import in.eko.eloka.partner.ApplicantClient;
import in.eko.eloka.partner.TestcontainersConfiguration;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.mysql.MySQLContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** The 10-digit application number: back-filled by V18, unique under parallel starts, in every snapshot. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ApplicationNumberIntegrationTest {

	private static final long FIRST = 1_000_000_001L;

	@Autowired
	MockMvc mvc;

	@Autowired
	JsonMapper json;

	@Autowired
	MySQLContainer mysql;

	@Test
	void parallelStartsGetDistinctTenDigitNumbers() throws Exception {
		int count = 12;
		List<ApplicantClient> clients = new ArrayList<>();
		List<String> tokens = new ArrayList<>();
		for (int i = 0; i < count; i++) {
			ApplicantClient client = new ApplicantClient(mvc, json);
			clients.add(client);
			tokens.add(client.signInNewApplicant());
		}
		ExecutorService pool = Executors.newFixedThreadPool(6);
		try {
			List<Future<JsonNode>> started = new ArrayList<>();
			for (int i = 0; i < count; i++) {
				ApplicantClient client = clients.get(i);
				String token = tokens.get(i);
				started.add(pool.submit(() -> client.body(mvc.perform(post("/v1/partner/applications")
					.header("Authorization", "Bearer " + token)).andExpect(status().isCreated()))));
			}
			Set<String> numbers = new HashSet<>();
			for (Future<JsonNode> result : started) {
				String number = result.get(30, TimeUnit.SECONDS).get("application_number").asString();
				assertThat(number).matches("[1-5]\\d{9}");
				numbers.add(number);
			}
			assertThat(numbers).hasSize(count);
		}
		finally {
			pool.shutdownNow();
		}
	}

	/** Rows that exist before V18 get numbers in creation order; new rows continue above them. */
	@Test
	void theMigrationBackFillsExistingApplications() throws Exception {
		String schema = "v18_check";
		String url = "jdbc:mysql://" + mysql.getHost() + ":" + mysql.getFirstMappedPort() + "/";
		try (Connection root = DriverManager.getConnection(url, "root", mysql.getPassword());
				Statement sql = root.createStatement()) {
			sql.execute("DROP DATABASE IF EXISTS " + schema);
			sql.execute("CREATE DATABASE " + schema);
		}
		Flyway.configure().dataSource(url + schema, "root", mysql.getPassword()).target("17").load().migrate();
		try (Connection db = DriverManager.getConnection(url + schema, "root", mysql.getPassword());
				Statement sql = db.createStatement()) {
			sql.execute("INSERT INTO applicant VALUES (UUID_TO_BIN(UUID()), x'00', x'"
					+ "00".repeat(32) + "', '******0001', 0, NOW(6), NOW(6))");
			for (int i = 0; i < 3; i++) {
				// different creation times, inserted out of order
				sql.execute("INSERT INTO partner_application (id, public_id, applicant_id, status, last_applicant_activity_at, "
						+ "expires_at, status_changed_at, version, created_at, updated_at) SELECT UUID_TO_BIN(UUID()), 'ptn_old" + i
						+ "', id, 'WITHDRAWN', NOW(6), NOW(6), NOW(6), 0, NOW(6) - INTERVAL " + (3 - i) + " DAY, NOW(6) FROM applicant");
			}
		}
		Flyway.configure().dataSource(url + schema, "root", mysql.getPassword()).load().migrate();
		try (Connection db = DriverManager.getConnection(url + schema, "root", mysql.getPassword());
				Statement sql = db.createStatement()) {
			List<Long> numbers = new ArrayList<>();
			try (ResultSet rows = sql.executeQuery(
					"SELECT application_number FROM partner_application ORDER BY public_id")) {
				while (rows.next()) {
					numbers.add(rows.getLong(1));
				}
			}
			assertThat(numbers).containsExactly(FIRST, FIRST + 1, FIRST + 2);
			sql.execute("INSERT INTO partner_application (id, public_id, applicant_id, status, last_applicant_activity_at, "
					+ "expires_at, status_changed_at, version, created_at, updated_at) SELECT UUID_TO_BIN(UUID()), 'ptn_new', id, "
					+ "'IN_PROGRESS', NOW(6), NOW(6), NOW(6), 0, NOW(6), NOW(6) FROM applicant");
			try (ResultSet row = sql.executeQuery(
					"SELECT application_number FROM partner_application WHERE public_id = 'ptn_new'")) {
				row.next();
				assertThat(row.getLong(1)).isEqualTo(FIRST + 3);
			}
			sql.execute("DROP DATABASE " + schema);
		}
	}
}
