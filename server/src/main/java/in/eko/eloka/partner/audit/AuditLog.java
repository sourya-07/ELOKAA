package in.eko.eloka.partner.audit;

/**
 * Append-only, hash-chained audit trail. Call it inside the same transaction as the change
 * it describes, so either both are saved or neither.
 */
public interface AuditLog {

	void record(AuditEntry entry);
}
