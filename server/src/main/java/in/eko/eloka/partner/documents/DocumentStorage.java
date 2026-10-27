package in.eko.eloka.partner.documents;

/** Where encrypted file bytes live. Implementations must be idempotent per key. */
public interface DocumentStorage {

	void put(String key, byte[] encryptedBytes);

	byte[] get(String key);

	void delete(String key);
}
