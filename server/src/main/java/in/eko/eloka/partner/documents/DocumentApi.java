package in.eko.eloka.partner.documents;

import java.util.UUID;

/** Public API of the documents module. Callers decide who may read which document. */
public interface DocumentApi {

	/**
	 * Checks, cleans, scans and stores a file.
	 *
	 * @throws in.eko.eloka.partner.shared.error.ValidationException when the file breaks its kind's rules
	 * @throws in.eko.eloka.partner.integration.ProviderNotConfiguredException for a PDF when no malware scanner is configured
	 */
	DocumentRef store(NewDocument document);

	/** @throws in.eko.eloka.partner.shared.error.NotFoundException when missing or owned by someone else */
	DocumentContent load(UUID ownerId, UUID documentId);

	/** Metadata only, for any owner (staff views). */
	DocumentRef describe(UUID documentId);

	/** Content for any owner (staff views); the caller has checked the staff member's rights. */
	DocumentContent loadForReview(UUID documentId);
}
