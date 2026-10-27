package in.eko.eloka.partner.identity.internal;

import java.time.Duration;

/** Freshly issued tokens. Shown to the caller once; only their hashes are stored. */
record TokenPair(String accessToken, String refreshToken, Duration expiresIn) {
}
