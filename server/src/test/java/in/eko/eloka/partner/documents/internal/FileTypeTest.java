package in.eko.eloka.partner.documents.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.concurrent.ThreadLocalRandom;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

/** The type comes from the bytes, and a cut-off or damaged file is not intact. */
class FileTypeTest {

	@Test
	void readsTheTypeFromTheBytesNotTheName() throws Exception {
		assertThat(FileType.sniff(image("jpeg"))).contains(FileType.JPEG);
		assertThat(FileType.sniff(image("png"))).contains(FileType.PNG);
		assertThat(FileType.sniff(pdf())).contains(FileType.PDF);
		assertThat(FileType.sniff("GIF89a".getBytes(StandardCharsets.ISO_8859_1))).isEmpty();
		assertThat(FileType.sniff(new byte[0])).isEmpty();
	}

	@Test
	void wholeFilesAreIntact() throws Exception {
		assertThat(FileType.JPEG.intact(image("jpeg"))).isTrue();
		assertThat(FileType.PNG.intact(image("png"))).isTrue();
		assertThat(FileType.PDF.intact(pdf())).isTrue();
		// data appended after a picture is removed later, it does not make the picture damaged
		byte[] appended = concat(image("jpeg"), "<script>".getBytes(StandardCharsets.ISO_8859_1));
		assertThat(FileType.JPEG.intact(appended)).isTrue();
	}

	@Test
	void cutOffFilesAreNot() throws Exception {
		byte[] jpeg = image("jpeg");
		byte[] png = image("png");
		byte[] pdf = pdf();
		assertThat(FileType.JPEG.intact(Arrays.copyOf(jpeg, jpeg.length - 100))).isFalse();
		assertThat(FileType.PNG.intact(Arrays.copyOf(png, png.length - 20))).isFalse();
		assertThat(FileType.PDF.intact(Arrays.copyOf(pdf, pdf.length - 8))).isFalse();
		assertThat(FileType.PDF.intact("%PDF-1.4\n%%EOF".getBytes(StandardCharsets.ISO_8859_1))).isFalse(); // no object at all
		assertThat(FileType.JPEG.intact(new byte[] { (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xD9 })).isFalse();
	}

	private static byte[] image(String format) throws Exception {
		BufferedImage image = new BufferedImage(300, 300, BufferedImage.TYPE_INT_RGB);
		for (int y = 0; y < 100; y++) {
			for (int x = 0; x < 100; x++) {
				image.setRGB(x, y, ThreadLocalRandom.current().nextInt(0xFFFFFF));
			}
		}
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		ImageIO.write(image, format, out);
		return out.toByteArray();
	}

	private static byte[] pdf() {
		return ("%PDF-1.4\n1 0 obj << >> endobj\n%" + "x".repeat(2048) + "\n%%EOF\n").getBytes(StandardCharsets.ISO_8859_1);
	}

	private static byte[] concat(byte[] a, byte[] b) {
		byte[] out = Arrays.copyOf(a, a.length + b.length);
		System.arraycopy(b, 0, out, a.length, b.length);
		return out;
	}
}
