/**
 * Operational commands that run instead of the web application: the one-shot migration job and
 * the staff bootstrap (docs/deploy/RUNBOOK.md).
 *
 * <p>Only types in this package (the module's public API) may be used by other modules;
 * sub-packages such as {@code internal} are private to the module.
 */
@ApplicationModule(displayName = "System")
package in.eko.eloka.partner.system;

import org.springframework.modulith.ApplicationModule;
