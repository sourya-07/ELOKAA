package in.eko.eloka.partner.documents;

import java.util.UUID;

/**
 * A file to store.
 *
 * @param ownerId the applicant the file belongs to
 * @param fileName as uploaded (cleaned before it is stored; never trusted for the type)
 */
public record NewDocument(UUID ownerId, DocumentKind kind, String fileName, byte[] bytes) {
}
