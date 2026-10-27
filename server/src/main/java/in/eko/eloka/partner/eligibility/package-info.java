/**
 * CIBIL eligibility (ADR-0005): asks the credit bureau through {@code integration.CreditBureau},
 * applies {@link in.eko.eloka.partner.eligibility.EligibilityPolicy} and records every assessment.
 * Only the score, the outcome and the provider's reference are kept, never the report.
 *
 * <p>Only types in this package (the module's public API) may be used by other modules;
 * sub-packages such as {@code internal} are private to the module.
 */
@ApplicationModule(displayName = "Eligibility")
package in.eko.eloka.partner.eligibility;

import org.springframework.modulith.ApplicationModule;
