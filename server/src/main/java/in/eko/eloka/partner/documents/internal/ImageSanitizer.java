package in.eko.eloka.partner.documents.internal;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;
import in.eko.eloka.partner.shared.error.ValidationException;

/**
 * Decodes an uploaded JPEG or PNG and writes a fresh JPEG from the pixels (transparency becomes white). What survives is only the
 * picture: EXIF data (camera, location), comments and anything appended after the image (polyglot
 * files) are gone. Images are limited to 40 megapixels before decoding, so a small file cannot
 * expand into a huge bitmap.
 */
final class ImageSanitizer {

	private static final long MAX_PIXELS = 40_000_000L;
	private static final float QUALITY = 0.9f;

	record Clean(byte[] bytes, int width, int height) {
	}

	private ImageSanitizer() {
	}

	static Clean reencodeJpeg(byte[] original) {
		// EXIF is dropped below, so turn the pixels upright first (phone photos carry their rotation there)
		BufferedImage image = ExifOrientation.upright(decode(original), ExifOrientation.read(original));
		BufferedImage rgb = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_RGB);
		rgb.createGraphics().drawImage(image, 0, 0, java.awt.Color.WHITE, null);
		ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
		ByteArrayOutputStream out = new ByteArrayOutputStream(original.length);
		try (ImageOutputStream stream = ImageIO.createImageOutputStream(out)) {
			writer.setOutput(stream);
			ImageWriteParam params = writer.getDefaultWriteParam();
			params.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
			params.setCompressionQuality(QUALITY);
			writer.write(null, new IIOImage(rgb, null, null), params);
		}
		catch (IOException ex) {
			throw unreadable();
		}
		finally {
			writer.dispose();
		}
		return new Clean(out.toByteArray(), rgb.getWidth(), rgb.getHeight());
	}

	private static BufferedImage decode(byte[] bytes) {
		try (ImageInputStream in = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
			Iterator<ImageReader> readers = ImageIO.getImageReaders(in);
			if (!readers.hasNext()) {
				throw unreadable();
			}
			ImageReader reader = readers.next();
			try {
				reader.setInput(in, true, true);
				if ((long) reader.getWidth(0) * reader.getHeight(0) > MAX_PIXELS) {
					throw ValidationException.field("file", "The picture is too large. Please use a smaller photo.");
				}
				return reader.read(0);
			}
			finally {
				reader.dispose();
			}
		}
		catch (IOException | RuntimeException ex) {
			if (ex instanceof ValidationException validation) {
				throw validation;
			}
			throw unreadable();
		}
	}

	private static ValidationException unreadable() {
		return ValidationException.field("file", "This picture is damaged or cannot be read. Save it again as a JPG or PNG and upload it.");
	}
}
