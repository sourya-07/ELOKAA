package in.eko.eloka.partner.documents.internal;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Optional;
import java.util.UUID;
import in.eko.eloka.partner.documents.DocumentApi;
import in.eko.eloka.partner.documents.DocumentContent;
import in.eko.eloka.partner.documents.DocumentKind;
import in.eko.eloka.partner.documents.DocumentRef;
import in.eko.eloka.partner.documents.DocumentStorage;
import in.eko.eloka.partner.documents.NewDocument;
import in.eko.eloka.partner.integration.MalwareScanner;
import in.eko.eloka.partner.shared.crypto.FieldCipher;
import in.eko.eloka.partner.shared.error.NotFoundException;
import in.eko.eloka.partner.shared.error.ValidationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

interface StoredDocumentRepository extends JpaRepository<StoredDocument, UUID> {

	Optional<StoredDocument> findByIdAndOwnerId(UUID id, UUID ownerId);
}

/**
 * Accepts a file only after: size within its kind's limit, real type (by bytes) allowed for its
 * kind, images decoded and re-encoded (metadata and trailing data removed, minimum size checked),
 * PDFs scanned for malware. Then the cleaned bytes are encrypted and stored.
 */
@Service
@Transactional(readOnly = true)
class DocumentService implements DocumentApi {

	private static final Logger log = LoggerFactory.getLogger(DocumentService.class);

	private final StoredDocumentRepository documents;
	private final DocumentStorage storage;
	private final MalwareScanner scanner;
	private final FieldCipher cipher;

	DocumentService(StoredDocumentRepository documents, DocumentStorage storage, MalwareScanner scanner,
			FieldCipher cipher) {
		this.documents = documents;
		this.storage = storage;
		this.scanner = scanner;
		this.cipher = cipher;
	}

	@Override
	@Transactional
	public DocumentRef store(NewDocument document) {
		DocumentKind.Rules rules = document.kind().rules();
		byte[] bytes = document.bytes();
		if (bytes == null || bytes.length == 0) {
			throw ValidationException.field("file", "The file is empty");
		}
		if (bytes.length > rules.maxBytes()) {
			throw ValidationException.field("file", "The file is larger than " + rules.maxBytes() / (1024 * 1024) + " MB");
		}
		FileType type = FileType.sniff(bytes).filter(t -> rules.contentTypes().contains(t.contentType()))
			.orElseThrow(() -> ValidationException.field("file",
					rules.acceptsPdf() ? "Only JPG or PDF files are accepted" : "Only JPG photos are accepted"));
		byte[] cleaned;
		int width = 0;
		int height = 0;
		if (type == FileType.JPEG) {
			ImageSanitizer.Clean clean = ImageSanitizer.reencodeJpeg(bytes);
			if (Math.min(clean.width(), clean.height()) < rules.minImageSide()) {
				throw ValidationException.field("file",
						"The photo is too small. It must be at least " + rules.minImageSide() + " pixels on each side.");
			}
			if (clean.bytes().length > rules.maxBytes()) {
				throw ValidationException.field("file",
						"The photo is larger than " + rules.maxBytes() / (1024 * 1024) + " MB. Please use a smaller photo.");
			}
			cleaned = clean.bytes();
			width = clean.width();
			height = clean.height();
		}
		else {
			if (scanner.scan(bytes) == MalwareScanner.Verdict.INFECTED) {
				log.warn("Rejected an infected upload of kind {}", document.kind());
				throw ValidationException.field("file", "This file cannot be accepted. Please upload a different copy.");
			}
			cleaned = bytes;
		}
		String key = UUID.randomUUID().toString();
		storage.put(key, cipher.encryptBytes(cleaned));
		deleteIfRolledBack(key);
		return documents.save(new StoredDocument(document.ownerId(), document.kind(), cleanName(document.fileName(), type),
				type.contentType(), cleaned.length, width, height, sha256(cleaned), key)).toRef();
	}

	@Override
	public DocumentContent load(UUID ownerId, UUID documentId) {
		StoredDocument doc = documents.findByIdAndOwnerId(documentId, ownerId).orElseThrow(DocumentService::notFound);
		return new DocumentContent(doc.toRef(), cipher.decryptBytes(storage.get(doc.getStorageKey())));
	}

	@Override
	public DocumentRef describe(UUID documentId) {
		return documents.findById(documentId).orElseThrow(DocumentService::notFound).toRef();
	}

	@Override
	public DocumentContent loadForReview(UUID documentId) {
		StoredDocument doc = documents.findById(documentId).orElseThrow(DocumentService::notFound);
		return new DocumentContent(doc.toRef(), cipher.decryptBytes(storage.get(doc.getStorageKey())));
	}

	/**
	 * The file is written before its row, so a later refusal in the same transaction (a duplicate file, a
	 * failed location check, a failed commit) would leave it on disk with nothing pointing to it. The key is
	 * new for every upload, so no committed row can point to a file deleted here.
	 */
	private void deleteIfRolledBack(String key) {
		TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
			@Override
			public void afterCompletion(int status) {
				if (status != STATUS_COMMITTED) {
					try {
						storage.delete(key);
					}
					catch (RuntimeException ex) {
						log.warn("Could not remove the file of a rolled-back upload", ex);
					}
				}
			}
		});
	}

	/** Keeps a readable base name; the extension always matches the real type. */
	static String cleanName(String original, FileType type) {
		String base = original == null ? "" : original.replaceAll("\\.[A-Za-z0-9]{1,5}$", "");
		base = base.replaceAll("[^A-Za-z0-9 _-]", "_").strip();
		if (base.isEmpty()) {
			base = "file";
		}
		if (base.length() > 100) {
			base = base.substring(0, 100);
		}
		return base + (type == FileType.PDF ? ".pdf" : ".jpg");
	}

	private static NotFoundException notFound() {
		return new NotFoundException("DOCUMENT_NOT_FOUND", "Document not found");
	}

	private static byte[] sha256(byte[] bytes) {
		try {
			return MessageDigest.getInstance("SHA-256").digest(bytes);
		}
		catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException(ex);
		}
	}
}
