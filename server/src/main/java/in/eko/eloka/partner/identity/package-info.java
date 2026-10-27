/**
 * Who is calling: applicants sign in with a mobile OTP (sent through
 * {@code integration.MobileOtpGateway}); sessions are opaque, database-backed bearer tokens
 * (only their SHA-256 is stored). Implements {@code shared.security.CallerResolver}.
 *
 * <p>Only types in this package (the module's public API) may be used by other modules;
 * sub-packages such as {@code internal} are private to the module.
 */
@ApplicationModule(displayName = "Identity")
package in.eko.eloka.partner.identity;

import org.springframework.modulith.ApplicationModule;
