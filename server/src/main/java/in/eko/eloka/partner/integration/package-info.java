/**
 * Ports to outside providers (ADR-0004). Each port has a real adapter (EPS first), a fake for
 * the local and test profiles only, and a "not configured" stub that refuses instead of
 * pretending to succeed. Which one runs is chosen by configuration, never by code.
 *
 * <p>Only types in this package (the module's public API) may be used by other modules;
 * sub-packages such as {@code internal} are private to the module.
 */
@ApplicationModule(displayName = "Integration")
package in.eko.eloka.partner.integration;

import org.springframework.modulith.ApplicationModule;
