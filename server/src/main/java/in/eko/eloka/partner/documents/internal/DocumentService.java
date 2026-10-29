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
 * Accepts a file only after: not empty, real type (by bytes, never the name or the browser's type)
 * allowed for its kind, size within its kind's minimum and maximum, the file complete (not cut off or
 * damaged), pictures (JPEG or PNG) decoded and re-encoded as JPEG (metadata and trailing data removed,
 * minimum pixel size checked), PDFs scanned for malware. Then the cleaned bytes are encrypted and stored.
 * A refused file changes nothing: the files already uploaded stay as they were.
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
			throw ValidationException.field("file", "The file is empty. Choose the file again.");
		}
		FileType type = FileType.sniff(bytes).filter(t -> rules.contentTypes().contains(t.contentType()))
			.orElseThrow(() -> ValidationException.field("file", "Only " + rules.formatNames() + " files are accepted."));
		if (bytes.length < rules.minBytes()) {
			throw ValidationException.field("file", "The file is smaller than " + size(rules.minBytes())
					+ ". It may be blank or damaged: upload a clear photo or scan.");
		}
		if (bytes.length > rules.maxBytes()) {
			throw ValidationException.field("file", "The file is larger than " + size(rules.maxBytes()) + ". Upload a smaller file.");
		}
		if (!type.intact(bytes)) {
			throw ValidationException.field("file", "This file is damaged or incomplete. Save it again and upload it.");
		}
		byte[] cleaned;
		int width = 0;
		int height = 0;
		if (type == FileType.JPEG || type == FileType.PNG) {
			ImageSanitizer.Clean clean = ImageSanitizer.reencodeJpeg(bytes);
			if (Math.min(clean.width(), clean.height()) < rules.minImageSide()) {
				throw ValidationException.field("file",
						"The photo is too small. It must be at least " + rules.minImageSide() + " pixels on each side.");
			}
			if (clean.bytes().length > rules.maxBytes()) {
				throw ValidationException.field("file", "The picture is larger than " + size(rules.maxBytes())
						+ " once cleaned. Upload a smaller picture.");
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
		// Pictures are stored as the JPEG they were re-encoded to (a PNG too)
		FileType stored = type == FileType.PNG ? FileType.JPEG : type;
		String key = UUID.randomUUID().toString();
		storage.put(key, cipher.encryptBytes(cleaned));
		deleteIfRolledBack(key);
		return documents.save(new StoredDocument(document.ownerId(), document.kind(), cleanName(document.fileName(), stored),
				stored.contentType(), cleaned.length, width, height, sha256(cleaned), key)).toRef();
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
		if (!TransactionSynchronizationManager.isSynchronizationActive()) {
			return;
		}
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

	/** "10 KB", "2 MB": how the limits are said to the applicant (1 KB = 1024 bytes). */
	static String size(long bytes) {
		return bytes >= 1024 * 1024 ? bytes / (1024 * 1024) + " MB" : bytes / 1024 + " KB";
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
