package in.eko.eloka.partner.documents;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** @param storagePath directory for the filesystem storage (mount a private volume there in production) */
@ConfigurationProperties("eloka.documents")
public record DocumentProperties(String storagePath) {

	public DocumentProperties {
		storagePath = storagePath == null || storagePath.isBlank() ? "data/files" : storagePath;
	}
}
