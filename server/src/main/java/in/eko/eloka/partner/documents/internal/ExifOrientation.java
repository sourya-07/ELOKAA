package in.eko.eloka.partner.documents.internal;

import java.awt.Graphics2D;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;

/**
 * Reads the EXIF orientation tag (0x0112) of a JPEG and turns the pixels upright. Phones save the
 * picture as the sensor saw it and only note the rotation in EXIF; {@link ImageSanitizer} drops all
 * EXIF data, so the rotation has to be applied to the pixels first or the photo is stored sideways.
 * A small, bounds-checked reader: anything unexpected means "no rotation".
 */
final class ExifOrientation {

	private ExifOrientation() {
	}

	/** @return 1–8 as in the EXIF standard; 1 (upright) when absent or unreadable */
	static int read(byte[] jpeg) {
		try {
			int pos = 2; // after SOI
			while (pos + 4 <= jpeg.length && (jpeg[pos] & 0xFF) == 0xFF) {
				int marker = jpeg[pos + 1] & 0xFF;
				int length = u16(jpeg, pos + 2, true);
				if (marker == 0xDA || length < 2) {
					return 1; // start of scan: no EXIF before the image data
				}
				if (marker == 0xE1 && length >= 8 && isExif(jpeg, pos + 4)) {
					return fromTiff(jpeg, pos + 10, pos + 2 + length);
				}
				pos += 2 + length;
			}
		}
		catch (IndexOutOfBoundsException ex) {
			// truncated or odd segment: treat as upright
		}
		return 1;
	}

	/** Applies orientation 2–8; returns the image unchanged for 1 or unknown values. */
	static BufferedImage upright(BufferedImage image, int orientation) {
		int w = image.getWidth();
		int h = image.getHeight();
		AffineTransform t = new AffineTransform();
		switch (orientation) {
			case 2 -> { t.translate(w, 0); t.scale(-1, 1); }
			case 3 -> { t.translate(w, h); t.rotate(Math.PI); }
			case 4 -> { t.translate(0, h); t.scale(1, -1); }
			case 5 -> { t.rotate(Math.PI / 2); t.scale(1, -1); }
			case 6 -> { t.translate(h, 0); t.rotate(Math.PI / 2); }
			case 7 -> { t.scale(-1, 1); t.translate(-h, 0); t.translate(0, w); t.rotate(3 * Math.PI / 2); }
			case 8 -> { t.translate(0, w); t.rotate(3 * Math.PI / 2); }
			default -> {
				return image;
			}
		}
		boolean swap = orientation >= 5;
		BufferedImage out = new BufferedImage(swap ? h : w, swap ? w : h, BufferedImage.TYPE_INT_RGB);
		// drawImage converts any decoded pixel layout (AffineTransformOp.filter refuses mixed types)
		Graphics2D g = out.createGraphics();
		g.drawImage(image, t, null);
		g.dispose();
		return out;
	}

	private static boolean isExif(byte[] b, int at) {
		return b[at] == 'E' && b[at + 1] == 'x' && b[at + 2] == 'i' && b[at + 3] == 'f' && b[at + 4] == 0 && b[at + 5] == 0;
	}

	private static int fromTiff(byte[] b, int tiff, int end) {
		boolean big = b[tiff] == 'M' && b[tiff + 1] == 'M';
		if (!big && !(b[tiff] == 'I' && b[tiff + 1] == 'I')) {
			return 1;
		}
		int ifd = tiff + u32(b, tiff + 4, big);
		int entries = u16(b, ifd, big);
		for (int i = 0; i < entries; i++) {
			int entry = ifd + 2 + i * 12;
			if (entry + 12 > end) {
				return 1;
			}
			if (u16(b, entry, big) == 0x0112) {
				int value = u16(b, entry + 8, big);
				return value >= 1 && value <= 8 ? value : 1;
			}
		}
		return 1;
	}

	private static int u16(byte[] b, int at, boolean big) {
		int x = b[at] & 0xFF;
		int y = b[at + 1] & 0xFF;
		return big ? (x << 8) | y : (y << 8) | x;
	}

	private static int u32(byte[] b, int at, boolean big) {
		return big ? (u16(b, at, true) << 16) | u16(b, at + 2, true) : (u16(b, at + 2, false) << 16) | u16(b, at, false);
	}
}
