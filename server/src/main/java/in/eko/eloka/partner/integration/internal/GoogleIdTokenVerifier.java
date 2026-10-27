package in.eko.eloka.partner.integration.internal;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import in.eko.eloka.partner.integration.GoogleIdentityVerifier;
import in.eko.eloka.partner.integration.ProviderNotConfiguredException;
import in.eko.eloka.partner.integration.ProviderUnavailableException;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.web.client.RestTemplate;

/**
 * Real Google ID token check: RS256 signature against Google's published keys, issuer
 * {@code accounts.google.com}, audience = the staff console's client id, not expired.
 */
class GoogleIdTokenVerifier implements GoogleIdentityVerifier {

	private static final String JWKS = "https://www.googleapis.com/oauth2/v3/certs";
	private static final List<String> ISSUERS = List.of("https://accounts.google.com", "accounts.google.com");

	private final String clientId;
	private volatile NimbusJwtDecoder decoder;

	GoogleIdTokenVerifier(String clientId) {
		this.clientId = clientId;
	}

	@Override
	public Optional<GoogleIdentity> verify(String idToken) {
		if (clientId == null || clientId.isBlank()) {
			throw new ProviderNotConfiguredException("Staff Google sign-in");
		}
		try {
			Jwt jwt = decoder().decode(idToken);
			Object verified = jwt.getClaims().get("email_verified");
			return Optional.of(new GoogleIdentity(jwt.getClaimAsString("email"),
					Boolean.TRUE.equals(verified) || "true".equals(String.valueOf(verified)), jwt.getClaimAsString("hd"),
					jwt.getClaimAsString("name")));
		}
		catch (BadJwtException ex) {
			return Optional.empty(); // a bad, expired or foreign token
		}
		catch (JwtException ex) {
			throw new ProviderUnavailableException("Staff Google sign-in"); // Google's keys could not be fetched
		}
	}

	private NimbusJwtDecoder decoder() {
		NimbusJwtDecoder current = decoder;
		if (current == null) {
			SimpleClientHttpRequestFactory timeouts = new SimpleClientHttpRequestFactory();
			timeouts.setConnectTimeout(Duration.ofSeconds(5));
			timeouts.setReadTimeout(Duration.ofSeconds(10));
			current = NimbusJwtDecoder.withJwkSetUri(JWKS).restOperations(new RestTemplate(timeouts)).build();
			OAuth2TokenValidator<Jwt> issuer = jwt -> ISSUERS.contains(String.valueOf(jwt.getClaims().get("iss")))
					? OAuth2TokenValidatorResult.success()
					: OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "Unexpected issuer", null));
			OAuth2TokenValidator<Jwt> audience = jwt -> jwt.getAudience() != null && jwt.getAudience().contains(clientId)
					? OAuth2TokenValidatorResult.success()
					: OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "Wrong audience", null));
			current.setJwtValidator(new DelegatingOAuth2TokenValidator<>(JwtValidators.createDefault(), issuer, audience));
			decoder = current;
		}
		return current;
	}
}
