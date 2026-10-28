package in.eko.eloka.partner.onboarding.internal;

import java.time.Instant;
import java.util.UUID;
import in.eko.eloka.partner.shared.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/**
 * Where the live photo was taken, as the browser's location service gave it at capture. The
 * coordinates are a precise personal location, so they are encrypted. EXIF location is still
 * stripped from the image itself (ADR-0006): this row is the only copy. A browser location can be
 * faked and a photo is not proof of liveness (docs/OPEN_QUESTIONS.md Q-3): evidence for staff only.
 */
@Entity
@Table(name = "photo_location")
class PhotoLocation extends BaseEntity {

	@Column(name = "application_id", nullable = false, columnDefinition = "BINARY(16)")
	private UUID applicationId;

	@Column(name = "document_id", nullable = false, unique = true, columnDefinition = "BINARY(16)")
	private UUID documentId;

	@Column(name = "latitude_enc", nullable = false, columnDefinition = "VARBINARY(64)")
	private byte[] latitudeEnc;

	@Column(name = "longitude_enc", nullable = false, columnDefinition = "VARBINARY(64)")
	private byte[] longitudeEnc;

	@Column(name = "accuracy_m", nullable = false)
	private double accuracyM;

	@Column(name = "captured_at", nullable = false)
	private Instant capturedAt;

	@Column(name = "received_at", nullable = false)
	private Instant receivedAt;

	/** Comma-separated: {@code LOW_ACCURACY}, {@code OUTSIDE_INDIA}; empty when nothing stood out. */
	@Column(nullable = false, length = 60)
	private String flags;

	protected PhotoLocation() {
	}

	PhotoLocation(UUID applicationId, UUID documentId, byte[] latitudeEnc, byte[] longitudeEnc, double accuracyM,
			Instant capturedAt, Instant receivedAt, String flags) {
		this.applicationId = applicationId;
		this.documentId = documentId;
		this.latitudeEnc = latitudeEnc;
		this.longitudeEnc = longitudeEnc;
		this.accuracyM = accuracyM;
		this.capturedAt = capturedAt;
		this.receivedAt = receivedAt;
		this.flags = flags;
	}

	UUID getDocumentId() {
		return documentId;
	}

	byte[] getLatitudeEnc() {
		return latitudeEnc;
	}

	byte[] getLongitudeEnc() {
		return longitudeEnc;
	}

	double getAccuracyM() {
		return accuracyM;
	}

	Instant getCapturedAt() {
		return capturedAt;
	}

	String getFlags() {
		return flags;
	}
}
