package in.eko.eloka.partner.documents;

import java.time.Instant;
import java.util.UUID;

/**
 * Metadata of a stored file (no content). Width and height are 0 for PDFs. {@code sha256} is of the
 * stored (cleaned) bytes: the same picture uploaded twice has the same value.
 */
public record DocumentRef(UUID id, UUID ownerId, DocumentKind kind, String fileName, String contentType, long size,
		int width, int height, Instant uploadedAt, byte[] sha256) {
}
