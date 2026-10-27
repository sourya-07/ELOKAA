package in.eko.eloka.partner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import in.eko.eloka.partner.audit.AuditEntry;
import in.eko.eloka.partner.audit.AuditLog;
import in.eko.eloka.partner.audit.AuditTrail;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

/** Boots the whole app against MySQL 8.4 (Flyway migrates from empty) and checks the foundation. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ElokaPartnerApplicationTests {

	@Autowired
	MockMvc mvc;

	@Autowired
	AuditLog auditLog;

	@Autowired
	AuditTrail auditTrail;

	@Autowired
	TransactionTemplate transactions;

	@Autowired
	JdbcTemplate jdbc;

	@Test
	void healthIsPublicAndCarriesARequestId() throws Exception {
		mvc.perform(get("/actuator/health"))
			.andExpect(status().isOk())
			.andExpect(header().exists("X-Request-Id"));
	}

	@Test
	void unknownRoutesAreDeniedWithProblemJson() throws Exception {
		mvc.perform(post("/v1/anything"))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
	}

	@Test
	void invalidBearerTokenIsNotACaller() throws Exception {
		mvc.perform(get("/v1/anything").header("Authorization", "Bearer made-up"))
			.andExpect(status().isUnauthorized());
	}

	@Test
	void auditEntriesAreChainedAndReadable() {
		transactions.executeWithoutResult(tx -> {
			auditLog.record(new AuditEntry("TEST_ONE", "SYSTEM", null, "TEST_TARGET", "t-1", Map.of("k", "v")));
			auditLog.record(new AuditEntry("TEST_TWO", "SYSTEM", null, "TEST_TARGET", "t-1", Map.of()));
		});
		var trail = auditTrail.forTarget("TEST_TARGET", List.of("t-1"), 10);
		assertThat(trail).extracting(AuditTrail.AuditRecord::action).containsExactly("TEST_ONE", "TEST_TWO");
		assertThat(trail.get(1).seq()).isEqualTo(trail.get(0).seq() + 1);
	}

	/** Recomputes every hash from the stored rows: proves the chain can be verified after the fact. */
	@Test
	void auditChainVerifiesFromStoredRows() {
		transactions.executeWithoutResult(tx -> auditLog
			.record(new AuditEntry("TEST_VERIFY", "STAFF", "s|1", "TEST_TARGET", "t-2", Map.of("a", "b|c"))));
		byte[][] previous = { new byte[32] };
		jdbc.query("SELECT * FROM audit_event ORDER BY seq", rs -> {
			String canonical = field(Long.toString(rs.getLong("seq")))
					+ field(rs.getObject("occurred_at", LocalDateTime.class).toInstant(ZoneOffset.UTC).toString())
					+ field(rs.getString("actor_type")) + field(rs.getString("actor_id")) + field(rs.getString("action"))
					+ field(rs.getString("target_type")) + field(rs.getString("target_id")) + field(rs.getString("details"));
			assertThat(rs.getBytes("prev_hash")).isEqualTo(previous[0]);
			byte[] expected = sha256(previous[0], canonical);
			assertThat(Arrays.equals(rs.getBytes("hash"), expected)).as("hash of seq %d", rs.getLong("seq")).isTrue();
			previous[0] = expected;
		});
	}

	private static String field(String value) {
		return (value == null ? "-" : value.length() + ":" + value) + ";";
	}

	private static byte[] sha256(byte[] previous, String canonical) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			digest.update(previous);
			digest.update(canonical.getBytes(StandardCharsets.UTF_8));
			return digest.digest();
		}
		catch (Exception ex) {
			throw new IllegalStateException(ex);
		}
	}
}
