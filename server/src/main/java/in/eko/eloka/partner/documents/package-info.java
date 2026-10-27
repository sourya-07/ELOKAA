/**
 * Uploaded files (photos, Aadhaar images, certificates, voter ID / driving licence). Each file
 * is checked by its bytes against the rules of its {@link in.eko.eloka.partner.documents.DocumentKind},
 * images are re-encoded as JPEG without metadata, PDFs are scanned for malware, and everything is
 * encrypted at rest behind the {@link in.eko.eloka.partner.documents.DocumentStorage} port (ADR-0006).
 * Callers decide who may read a file back.
 *
 * <p>Only types in this package (the module's public API) may be used by other modules;
 * sub-packages such as {@code internal} are private to the module.
 */
@ApplicationModule(displayName = "Documents")
package in.eko.eloka.partner.documents;

import org.springframework.modulith.ApplicationModule;
