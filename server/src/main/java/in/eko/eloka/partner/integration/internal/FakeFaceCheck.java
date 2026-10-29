package in.eko.eloka.partner.integration.internal;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.List;
import javax.imageio.ImageIO;
import in.eko.eloka.partner.integration.FaceCheck;
import in.eko.eloka.partner.shared.config.UnsafeInProduction;

/**
 * Local and test only: answers by the colour of the picture's top-left corner (16 px), so tests can ask
 * for each case: black = no face, white = two faces, red = one face with eyes closed, anything else = one
 * centred face with eyes open.
 */
class FakeFaceCheck implements FaceCheck, UnsafeInProduction {

	private static final Face CENTRED = new Face(0.5, 0.45, 0.4);

	@Override
	public boolean enabled() {
		return true;
	}

	@Override
	public Result analyse(byte[] picture) {
		BufferedImage image;
		try {
			image = ImageIO.read(new ByteArrayInputStream(picture));
		}
		catch (IOException ex) {
			image = null;
		}
		if (image == null) {
			return new Result(List.of(), Eyes.UNKNOWN);
		}
		long r = 0;
		long g = 0;
		long b = 0;
		int size = Math.min(16, Math.min(image.getWidth(), image.getHeight()));
		for (int y = 0; y < size; y++) {
			for (int x = 0; x < size; x++) {
				int rgb = image.getRGB(x, y);
				r += (rgb >> 16) & 0xFF;
				g += (rgb >> 8) & 0xFF;
				b += rgb & 0xFF;
			}
		}
		int n = size * size;
		r /= n;
		g /= n;
		b /= n;
		if (r < 40 && g < 40 && b < 40) {
			return new Result(List.of(), Eyes.UNKNOWN);
		}
		if (r > 215 && g > 215 && b > 215) {
			return new Result(List.of(CENTRED, new Face(0.8, 0.45, 0.2)), Eyes.OPEN);
		}
		if (r > 180 && g < 80 && b < 80) {
			return new Result(List.of(CENTRED), Eyes.CLOSED);
		}
		return new Result(List.of(CENTRED), Eyes.OPEN);
	}

	@Override
	public String unsafeReason() {
		return "fake face check is active (answers by the picture's corner colour)";
	}
}
