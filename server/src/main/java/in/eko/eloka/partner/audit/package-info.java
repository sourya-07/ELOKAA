/**
 * Append-only audit trail with a hash chain. Kept apart from the operational logs:
 * business decisions are recorded here, not only in log files.
 *
 * <p>Public API: {@link in.eko.eloka.partner.audit.AuditLog}, {@link in.eko.eloka.partner.audit.AuditTrail}.
 * Only types in this package (the module's public API) may be used by other modules;
 * sub-packages such as {@code internal} are private to the module.
 */
@ApplicationModule(displayName = "Audit")
package in.eko.eloka.partner.audit;

import org.springframework.modulith.ApplicationModule;
