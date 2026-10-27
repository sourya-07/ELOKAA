/**
 * Cross-cutting building blocks every module may use: error model, web plumbing, security.
 * Keep it small. Business rules never live here.
 */
@ApplicationModule(displayName = "Shared", type = ApplicationModule.Type.OPEN)
package in.eko.eloka.partner.shared;

import org.springframework.modulith.ApplicationModule;
