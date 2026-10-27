/**
 * The partner application: one per applicant at a time, its steps and their rules
 * (docs/PARTNER_JOURNEY.md), the application status machine, consent records, submission and
 * expiry. Orchestrates the verification, eligibility and documents modules: the browser never
 * marks a step done, the server does after the check passes.
 *
 * <p>Only types in this package (the module's public API) may be used by other modules;
 * sub-packages such as {@code internal} are private to the module.
 */
@ApplicationModule(displayName = "Onboarding")
package in.eko.eloka.partner.onboarding;

import org.springframework.modulith.ApplicationModule;
