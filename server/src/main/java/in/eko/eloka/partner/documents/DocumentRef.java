package in.eko.eloka.partner.documents;

import java.time.Instant;
import java.util.UUID;

/** Metadata of a stored file (no content). Width and height are 0 for PDFs. */
public record DocumentRef(UUID id, UUID ownerId, DocumentKind kind, String fileName, String contentType, long size,
		int width, int height, Instant uploadedAt) {
}
