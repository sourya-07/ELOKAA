package in.eko.eloka.partner.documents.internal;

import java.util.UUID;
import in.eko.eloka.partner.documents.DocumentKind;
import in.eko.eloka.partner.documents.DocumentRef;
import in.eko.eloka.partner.shared.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

/** Metadata of a stored file. The bytes live in {@code DocumentStorage}, encrypted. */
@Entity
@Table(name = "stored_document")
class StoredDocument extends BaseEntity {

	@Column(name = "owner_id", nullable = false, columnDefinition = "BINARY(16)")
	private UUID ownerId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 30)
	private DocumentKind kind;

	@Column(name = "file_name", nullable = false, length = 120)
	private String fileName;

	@Column(name = "content_type", nullable = false, length = 50)
	private String contentType;

	@Column(name = "size_bytes", nullable = false)
	private long sizeBytes;

	@Column(nullable = false)
	private int width;

	@Column(nullable = false)
	private int height;

	/** SHA-256 of the stored (cleaned) bytes. */
	@Column(nullable = false, columnDefinition = "BINARY(32)")
	private byte[] sha256;

	@Column(name = "storage_key", nullable = false, unique = true, length = 36)
	private String storageKey;

	protected StoredDocument() {
	}

	StoredDocument(UUID ownerId, DocumentKind kind, String fileName, String contentType, long sizeBytes, int width,
			int height, byte[] sha256, String storageKey) {
		this.ownerId = ownerId;
		this.kind = kind;
		this.fileName = fileName;
		this.contentType = contentType;
		this.sizeBytes = sizeBytes;
		this.width = width;
		this.height = height;
		this.sha256 = sha256;
		this.storageKey = storageKey;
	}

	String getStorageKey() {
		return storageKey;
	}

	DocumentRef toRef() {
		return new DocumentRef(getId(), ownerId, kind, fileName, contentType, sizeBytes, width, height, getCreatedAt());
	}
}
