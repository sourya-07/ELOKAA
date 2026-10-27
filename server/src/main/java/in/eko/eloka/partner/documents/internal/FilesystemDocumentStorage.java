package in.eko.eloka.partner.documents.internal;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import in.eko.eloka.partner.documents.DocumentProperties;
import in.eko.eloka.partner.documents.DocumentStorage;
import org.springframework.stereotype.Component;

/** Stores encrypted files under {@code eloka.documents.storage-path}, two-level fan-out by key. */
@Component
class FilesystemDocumentStorage implements DocumentStorage {

	private final Path root;

	FilesystemDocumentStorage(DocumentProperties properties) {
		this.root = Path.of(properties.storagePath()).toAbsolutePath().normalize();
	}

	@Override
	public void put(String key, byte[] encryptedBytes) {
		Path target = resolve(key);
		try {
			Files.createDirectories(target.getParent());
			Path temp = Files.createTempFile(target.getParent(), "upload", ".tmp");
			Files.write(temp, encryptedBytes);
			Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
		}
		catch (IOException ex) {
			throw new UncheckedIOException("Could not store document", ex);
		}
	}

	@Override
	public byte[] get(String key) {
		try {
			return Files.readAllBytes(resolve(key));
		}
		catch (IOException ex) {
			throw new UncheckedIOException("Could not read document", ex);
		}
	}

	@Override
	public void delete(String key) {
		try {
			Files.deleteIfExists(resolve(key));
		}
		catch (IOException ex) {
			throw new UncheckedIOException("Could not delete document", ex);
		}
	}

	private Path resolve(String key) {
		if (!key.matches("[a-f0-9-]{36}")) {
			throw new IllegalArgumentException("Invalid storage key");
		}
		return root.resolve(key.substring(0, 2)).resolve(key);
	}
}
