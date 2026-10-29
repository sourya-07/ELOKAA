package in.eko.eloka.partner.integration;

import java.util.List;

/**
 * Finds faces in a photo, on our own server (no picture leaves it): how many, where, and whether the
 * largest one's eyes look open. A check on the picture, not certified liveness and not who the person is
 * (docs/OPEN_QUESTIONS.md C-12).
 */
public interface FaceCheck {

	enum Eyes {
		OPEN,
		CLOSED,
		UNKNOWN
	}

	/** One face, relative to the picture: centre and width as shares of the picture's width and height (0 to 1). */
	record Face(double centreX, double centreY, double width) {
	}

	/** The faces found, largest first, and the eyes of the largest. */
	record Result(List<Face> faces, Eyes eyes) {

		public Face largest() {
			return faces.isEmpty() ? null : faces.getFirst();
		}
	}

	/** False when no face check is configured: the photos are then taken without it. */
	boolean enabled();

	/**
	 * @return no faces for a picture that cannot be read
	 * @throws ProviderUnavailableException when the check itself cannot run
	 */
	Result analyse(byte[] picture);
}
