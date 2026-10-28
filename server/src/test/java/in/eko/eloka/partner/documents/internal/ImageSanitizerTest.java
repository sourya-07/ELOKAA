package in.eko.eloka.partner.documents.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

/** Phone photos carry their rotation in EXIF; the re-encoded copy must come out upright. */
class ImageSanitizerTest {

	@Test
	void rotatesAPhotoTakenOnItsSide() throws Exception {
		byte[] sideways = withOrientation(landscape(400, 200), 6, false); // stored 400x200, shown rotated 90°
		ImageSanitizer.Clean clean = ImageSanitizer.reencodeJpeg(sideways);
		assertThat(clean.width()).isEqualTo(200);
		assertThat(clean.height()).isEqualTo(400);
		// the red left half ends up on top after a clockwise turn
		BufferedImage out = ImageIO.read(new ByteArrayInputStream(clean.bytes()));
		assertThat(isRed(out.getRGB(100, 50))).isTrue();
		assertThat(isRed(out.getRGB(100, 350))).isFalse();
	}

	@Test
	void readsBigEndianExifAndUpsideDownPhotos() throws Exception {
		byte[] upsideDown = withOrientation(landscape(400, 200), 3, true);
		assertThat(ExifOrientation.read(upsideDown)).isEqualTo(3);
		BufferedImage out = ImageIO.read(new ByteArrayInputStream(ImageSanitizer.reencodeJpeg(upsideDown).bytes()));
		assertThat(isRed(out.getRGB(300, 100))).isTrue(); // red moved from the left to the right
	}

	@Test
	void everyOrientationMovesTheTopLeftCornerToTheRightPlace() {
		// where a mark in the stored top-left corner must end up: T/B = top/bottom, L/R = left/right
		String[] expected = { null, "TL", "TR", "BR", "BL", "TL", "TR", "BR", "BL" };
		for (int orientation = 1; orientation <= 8; orientation++) {
			BufferedImage stored = new BufferedImage(400, 200, BufferedImage.TYPE_3BYTE_BGR);
			Graphics2D g = stored.createGraphics();
			g.setColor(Color.RED);
			g.fillRect(0, 0, 40, 40);
			g.dispose();
			BufferedImage out = ExifOrientation.upright(stored, orientation);
			boolean swapped = orientation >= 5;
			assertThat(out.getWidth()).as("width for " + orientation).isEqualTo(swapped ? 200 : 400);
			int w = out.getWidth();
			int h = out.getHeight();
			String corner = isRed(out.getRGB(10, 10)) ? "TL" : isRed(out.getRGB(w - 10, 10)) ? "TR"
					: isRed(out.getRGB(w - 10, h - 10)) ? "BR" : isRed(out.getRGB(10, h - 10)) ? "BL" : "none";
			assertThat(corner).as("orientation " + orientation).isEqualTo(expected[orientation]);
		}
	}

	@Test
	void leavesPhotosWithoutExifAsTheyAre() throws Exception {
		byte[] plain = jpeg(landscape(400, 200));
		assertThat(ExifOrientation.read(plain)).isEqualTo(1);
		assertThat(ImageSanitizer.reencodeJpeg(plain).width()).isEqualTo(400);
		assertThat(ExifOrientation.read(new byte[] { (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE1, 0 })).isEqualTo(1);
	}

	private static BufferedImage landscape(int w, int h) {
		BufferedImage image = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
		Graphics2D g = image.createGraphics();
		g.setColor(Color.RED);
		g.fillRect(0, 0, w / 2, h);
		g.setColor(Color.BLUE);
		g.fillRect(w / 2, 0, w - w / 2, h);
		g.dispose();
		return image;
	}

	private static boolean isRed(int rgb) {
		Color c = new Color(rgb);
		return c.getRed() > 180 && c.getBlue() < 80;
	}

	private static byte[] jpeg(BufferedImage image) throws Exception {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		ImageIO.write(image, "jpeg", out);
		return out.toByteArray();
	}

	/** Inserts an APP1 Exif segment with one IFD0 entry (Orientation) right after SOI. */
	private static byte[] withOrientation(BufferedImage image, int orientation, boolean bigEndian) throws Exception {
		byte[] plain = jpeg(image);
		ByteArrayOutputStream tiff = new ByteArrayOutputStream();
		if (bigEndian) {
			tiff.write(new byte[] { 'M', 'M', 0, 42, 0, 0, 0, 8, 0, 1, 0x01, 0x12, 0, 3, 0, 0, 0, 1, 0, (byte) orientation, 0, 0, 0, 0, 0, 0 });
		}
		else {
			tiff.write(new byte[] { 'I', 'I', 42, 0, 8, 0, 0, 0, 1, 0, 0x12, 0x01, 3, 0, 1, 0, 0, 0, (byte) orientation, 0, 0, 0, 0, 0, 0, 0 });
		}
		byte[] body = tiff.toByteArray();
		int length = 2 + 6 + body.length;
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		out.write(plain, 0, 2); // SOI
		out.write(new byte[] { (byte) 0xFF, (byte) 0xE1, (byte) (length >> 8), (byte) length, 'E', 'x', 'i', 'f', 0, 0 });
		out.write(body);
		out.write(plain, 2, plain.length - 2);
		return out.toByteArray();
	}
}
