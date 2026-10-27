package in.eko.eloka.partner.documents;

/** A stored file with its decrypted bytes. */
public record DocumentContent(DocumentRef ref, byte[] bytes) {
}
