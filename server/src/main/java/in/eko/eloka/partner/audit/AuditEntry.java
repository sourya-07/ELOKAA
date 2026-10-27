package in.eko.eloka.partner.audit;

import java.util.Map;

/**
 * One audited action. {@code details} must never contain personal data (no mobiles,
 * names, PAN); use ids and codes.
 *
 * @param action what happened, e.g. {@code LOGIN_SUCCEEDED}
 * @param actorType {@code APPLICANT}, {@code STAFF}, {@code SYSTEM} or {@code ANONYMOUS}
 * @param actorId user/staff id or {@code null}
 * @param targetType kind of thing acted on, e.g. {@code PARTNER_APPLICATION}
 * @param targetId its id
 * @param details small, non-personal key/values
 */
public record AuditEntry(String action, String actorType, String actorId, String targetType, String targetId,
		Map<String, String> details) {

	public AuditEntry {
		details = details == null ? Map.of() : Map.copyOf(details);
	}
}
