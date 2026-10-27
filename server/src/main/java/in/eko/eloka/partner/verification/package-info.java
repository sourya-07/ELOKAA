/**
 * Identity checks of an application: PAN (provider check of number, name and date of birth),
 * Aadhaar through DigiLocker (only the last 4 digits ever arrive) or by a masked image that staff
 * check by hand. Keeps every attempt and its evidence status; personal values are encrypted,
 * with a masked copy for display and a blind index where uniqueness matters.
 *
 * <p>Only types in this package (the module's public API) may be used by other modules;
 * sub-packages such as {@code internal} are private to the module.
 */
@ApplicationModule(displayName = "Verification")
package in.eko.eloka.partner.verification;

import org.springframework.modulith.ApplicationModule;
