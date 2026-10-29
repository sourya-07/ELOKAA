package in.eko.eloka.partner.integration.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.concurrent.ThreadLocalRandom;
import javax.imageio.ImageIO;
import in.eko.eloka.partner.integration.FaceCheck;
import org.junit.jupiter.api.Test;

/**
 * The real OpenCV check loads its native library and models, and finds no face where there is none.
 * The repository is public and holds no photos of people, so finding a real face is checked by hand
 * (docs/TESTING.md).
 */
class OpenCvFaceCheckTest {

	private final OpenCvFaceCheck check = new OpenCvFaceCheck();

	@Test
	void findsNoFaceInNoiseOrABlankPicture() throws Exception {
		assertThat(check.enabled()).isTrue();
		assertThat(check.analyse(picture(true)).faces()).isEmpty();
		assertThat(check.analyse(picture(false)).faces()).isEmpty();
	}

	@Test
	void anUnreadablePictureHasNoFace() {
		FaceCheck.Result result = check.analyse("not a picture".getBytes());
		assertThat(result.faces()).isEmpty();
		assertThat(result.eyes()).isEqualTo(FaceCheck.Eyes.UNKNOWN);
	}

	private static byte[] picture(boolean noise) throws Exception {
		BufferedImage image = new BufferedImage(800, 600, BufferedImage.TYPE_INT_RGB);
		for (int y = 0; y < 600; y++) {
			for (int x = 0; x < 800; x++) {
				image.setRGB(x, y, noise ? ThreadLocalRandom.current().nextInt(0xFFFFFF) : 0x8899AA);
			}
		}
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		ImageIO.write(image, "jpeg", out);
		return out.toByteArray();
	}
}
