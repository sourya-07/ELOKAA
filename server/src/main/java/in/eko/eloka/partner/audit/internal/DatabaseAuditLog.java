package in.eko.eloka.partner.audit.internal;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.TreeMap;
import in.eko.eloka.partner.audit.AuditEntry;
import in.eko.eloka.partner.audit.AuditLog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/**
 * Stores each entry with {@code hash = SHA-256(prev_hash ‖ canonical entry)}, so editing or
 * deleting any row breaks the chain from that point on. The canonical form length-prefixes
 * every field (no separator can be forged inside a value) and uses the time exactly as the
 * database stores it (microseconds), so the chain can be re-verified from the rows. Also mirrors the entry to the
 * {@code eloka.audit} logger.
 */
@Service
class DatabaseAuditLog implements AuditLog {

	private static final Logger auditLog = LoggerFactory.getLogger("eloka.audit");

	private final AuditEventRepository events;
	private final AuditChainHeadRepository heads;
	private final JsonMapper json;
	private final Clock clock;

	DatabaseAuditLog(AuditEventRepository events, AuditChainHeadRepository heads, JsonMapper json, Clock clock) {
		this.events = events;
		this.heads = heads;
		this.json = json;
		this.clock = clock;
	}

	@Override
	@Transactional
	public void record(AuditEntry entry) {
		Instant now = Instant.now(clock).truncatedTo(ChronoUnit.MICROS); // DATETIME(6) precision
		String details = json.writeValueAsString(new TreeMap<>(entry.details()));
		AuditChainHead head = heads.lockHead();
		long seq = head.lastSeq() + 1;
		String canonical = canonical(Long.toString(seq), now.toString(), entry.actorType(), entry.actorId(),
				entry.action(), entry.targetType(), entry.targetId(), details);
		byte[] hash = sha256(head.lastHash(), canonical);

		events.save(new AuditEvent(seq, now, entry.actorType(), entry.actorId(), entry.action(),
				entry.targetType(), entry.targetId(), details, head.lastHash(), hash));
		head.advance(seq, hash);

		auditLog.info("seq={} action={} actor={}:{} target={}:{} details={}", seq, entry.action(), entry.actorType(),
				entry.actorId(), entry.targetType(), entry.targetId(), details);
	}

	/** {@code <length>:<value>} per field; {@code -} for null, which no length prefix can produce. */
	private static String canonical(String... fields) {
		StringBuilder out = new StringBuilder();
		for (String field : fields) {
			out.append(field == null ? "-" : field.length() + ":" + field).append(';');
		}
		return out.toString();
	}

	private static byte[] sha256(byte[] previous, String canonical) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			digest.update(previous);
			digest.update(canonical.getBytes(StandardCharsets.UTF_8));
			return digest.digest();
		}
		catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException(ex);
		}
	}
}
