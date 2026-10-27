package in.eko.eloka.partner.audit.internal;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import in.eko.eloka.partner.audit.AuditTrail;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

@Component
class JdbcAuditTrail implements AuditTrail {

	private final NamedParameterJdbcTemplate jdbc;

	JdbcAuditTrail(NamedParameterJdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	@Override
	public List<AuditRecord> forTarget(String targetType, Collection<String> targetIds, int limit) {
		if (targetIds.isEmpty()) {
			return List.of();
		}
		// Newest first so a long trail keeps its latest events, then turned to oldest first
		List<AuditRecord> latest = new ArrayList<>(jdbc.query("""
				SELECT seq, occurred_at, actor_type, actor_id, action, target_type, target_id, details
				FROM audit_event WHERE target_type = :type AND target_id IN (:ids) ORDER BY seq DESC LIMIT :limit""",
				new MapSqlParameterSource("type", targetType).addValue("ids", targetIds)
					.addValue("limit", Math.max(1, Math.min(limit, 1000))),
				(rs, row) -> new AuditRecord(rs.getLong("seq"),
						// DATETIME holds UTC; getTimestamp would read it in the JVM's zone
						rs.getObject("occurred_at", LocalDateTime.class).toInstant(ZoneOffset.UTC),
						rs.getString("actor_type"), rs.getString("actor_id"), rs.getString("action"),
						rs.getString("target_type"), rs.getString("target_id"), rs.getString("details"))));
		Collections.reverse(latest);
		return latest;
	}
}
