package in.eko.eloka.partner.shared.crypto;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Keys for personal data at rest. Each is 32 random bytes, base64-encoded.
 * Generate with {@code openssl rand -base64 32}.
 *
 * @param fieldKey AES-256-GCM key for encrypting values (mobile, PAN, address, documents)
 * @param indexKey HMAC-SHA256 key for searchable "blind index" hashes of the same values
 */
@ConfigurationProperties("eloka.crypto")
public record CryptoProperties(String fieldKey, String indexKey) {
}
