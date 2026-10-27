package in.eko.eloka.partner.documents.internal;

import java.util.Optional;

/** Detects the real file type from its first bytes (never trusts the name or the browser). */
enum FileType {
	JPEG("image/jpeg"),
	PNG("image/png"),
	PDF("application/pdf");

	private final String contentType;

	FileType(String contentType) {
		this.contentType = contentType;
	}

	String contentType() {
		return contentType;
	}

	static Optional<FileType> sniff(byte[] bytes) {
		if (bytes.length >= 3 && (bytes[0] & 0xFF) == 0xFF && (bytes[1] & 0xFF) == 0xD8 && (bytes[2] & 0xFF) == 0xFF) {
			return Optional.of(JPEG);
		}
		if (bytes.length >= 8 && (bytes[0] & 0xFF) == 0x89 && bytes[1] == 'P' && bytes[2] == 'N' && bytes[3] == 'G') {
			return Optional.of(PNG);
		}
		if (bytes.length >= 5 && bytes[0] == '%' && bytes[1] == 'P' && bytes[2] == 'D' && bytes[3] == 'F' && bytes[4] == '-') {
			return Optional.of(PDF);
		}
		return Optional.empty();
	}
}
