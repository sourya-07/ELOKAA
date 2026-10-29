package in.eko.eloka.partner.integration.internal;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * The face check on photos, under {@code eloka.integration.face-check} (docs/OPEN_QUESTIONS.md C-12).
 *
 * @param provider {@code none} (no check), {@code fake} (local/test only) or {@code opencv} (on our server)
 */
@ConfigurationProperties("eloka.integration.face-check")
record FaceCheckProperties(Provider provider) {

	enum Provider {
		NONE,
		FAKE,
		OPENCV
	}

	FaceCheckProperties {
		provider = provider == null ? Provider.NONE : provider;
	}
}
