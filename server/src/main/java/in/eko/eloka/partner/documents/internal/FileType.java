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

	/**
	 * Whether the file reaches its proper end, so a cut-off upload or a damaged copy is refused before
	 * anything else: a JPEG has its end-of-image marker (data appended after it is removed later), a PNG
	 * its IEND chunk, a PDF its %%EOF near the end. Pictures are also decoded in full afterwards.
	 */
	boolean intact(byte[] bytes) {
		return switch (this) {
			// the end marker must come after the image data starts (header tables could hold the same two bytes)
			case JPEG -> {
				int scan = jpegScanStart(bytes);
				yield scan > 0 && lastIndexOf(bytes, new byte[] { (byte) 0xFF, (byte) 0xD9 }, scan) > scan;
			}
			case PNG -> lastIndexOf(bytes, new byte[] { 'I', 'E', 'N', 'D' }, 0) > 8;
			// %%EOF within the last 1 KB (a final newline or two may follow it), and at least one object
			case PDF -> lastIndexOf(bytes, "%%EOF".getBytes(java.nio.charset.StandardCharsets.ISO_8859_1), Math.max(0, bytes.length - 1024)) >= 0
					&& lastIndexOf(bytes, " obj".getBytes(java.nio.charset.StandardCharsets.ISO_8859_1), 0) > 0;
		};
	}

	/** Where the first start-of-scan segment is, walking the header segments by their lengths; -1 if none. */
	private static int jpegScanStart(byte[] bytes) {
		int pos = 2; // after SOI
		while (pos + 4 <= bytes.length && (bytes[pos] & 0xFF) == 0xFF) {
			int marker = bytes[pos + 1] & 0xFF;
			if (marker == 0xDA) {
				return pos;
			}
			int length = ((bytes[pos + 2] & 0xFF) << 8) | (bytes[pos + 3] & 0xFF);
			if (length < 2) {
				return -1;
			}
			pos += 2 + length;
		}
		return -1;
	}

	/** The last position of {@code needle} at or after {@code from}, or -1. */
	private static int lastIndexOf(byte[] bytes, byte[] needle, int from) {
		outer:
		for (int i = bytes.length - needle.length; i >= from; i--) {
			for (int j = 0; j < needle.length; j++) {
				if (bytes[i + j] != needle[j]) {
					continue outer;
				}
			}
			return i;
		}
		return -1;
	}
}
