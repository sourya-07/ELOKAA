package in.eko.eloka.partner.identity.internal;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import com.fasterxml.jackson.annotation.JsonProperty;
import in.eko.eloka.partner.identity.IdentityProperties;
import in.eko.eloka.partner.shared.crypto.FieldCipher;
import in.eko.eloka.partner.shared.crypto.Mobiles;
import in.eko.eloka.partner.shared.error.ApiProblem;
import in.eko.eloka.partner.shared.error.ValidationException;
import in.eko.eloka.partner.shared.security.Caller;
import in.eko.eloka.partner.shared.security.CallerContext;
import in.eko.eloka.partner.shared.security.SessionKind;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Applicant sign-in. Answers are the same for known and unknown mobiles, so the API does not
 * reveal who has applied. The caller is the client's server-side proxy (ADR-0003), never a
 * browser holding these tokens.
 */
@RestController
@RequestMapping("/v1/partner")
class ApplicantSessionController {

	private static final String OTP_REF_PREFIX = "otpref_";

	private final ApplicantSignIn signIn;
	private final SessionService sessions;
	private final ApplicantReauthService reauth;
	private final FieldCipher cipher;
	private final IdentityProperties properties;
	private final Clock clock;

	ApplicantSessionController(ApplicantSignIn signIn, SessionService sessions, ApplicantReauthService reauth,
			FieldCipher cipher, IdentityProperties properties, Clock clock) {
		this.signIn = signIn;
		this.sessions = sessions;
		this.reauth = reauth;
		this.cipher = cipher;
		this.properties = properties;
		this.clock = clock;
	}

	record SendOtpRequest(String mobile) {
	}

	record SendOtpResponse(@JsonProperty("otp_ref") String otpRef, @JsonProperty("expires_in") long expiresIn,
			@JsonProperty("resend_after") long resendAfter, @JsonProperty("otp_length") int otpLength) {
	}

	record VerifyOtpRequest(@JsonProperty("otp_ref") String otpRef, String otp) {
	}

	record RefreshRequest(@JsonProperty("refresh_token") String refreshToken) {
	}

	record TokenResponse(@JsonProperty("access_token") String accessToken,
			@JsonProperty("refresh_token") String refreshToken, @JsonProperty("token_type") String tokenType,
			@JsonProperty("expires_in") long expiresIn) {

		static TokenResponse of(TokenPair tokens) {
			return new TokenResponse(tokens.accessToken(), tokens.refreshToken(), "Bearer",
					tokens.expiresIn().toSeconds());
		}
	}

	@PostMapping("/otp")
	ResponseEntity<SendOtpResponse> sendOtp(@RequestBody SendOtpRequest body, HttpServletRequest request) {
		String mobile = Mobiles.normalize(body == null ? null : body.mobile());
		signIn.sendOtp(mobile, request.getRemoteAddr());
		return ResponseEntity.accepted()
			.body(new SendOtpResponse(otpRef(mobile), properties.otpTtl().toSeconds(),
					properties.otpMinGap().toSeconds(), properties.otpLength()));
	}

	@PostMapping("/otp/verify")
	TokenResponse verifyOtp(@RequestBody VerifyOtpRequest body) {
		if (body == null || body.otp() == null || body.otp().isBlank()) {
			throw ValidationException.field("otp", "Enter the code we sent you");
		}
		String mobile = mobileFromRef(body.otpRef());
		return switch (signIn.verify(mobile, body.otp())) {
			case ApplicantSignIn.Outcome.SignedIn signedIn -> TokenResponse.of(signedIn.tokens());
			case ApplicantSignIn.Outcome.Expired expired -> throw expired();
			case ApplicantSignIn.Outcome.WrongCode wrong when wrong.attemptsLeft() == 0 ->
				throw new ApiProblem(HttpStatus.TOO_MANY_REQUESTS, "OTP_LOCKED",
						"Too many wrong attempts. Please request a new code.");
			case ApplicantSignIn.Outcome.WrongCode wrong -> throw new ApiProblem(HttpStatus.UNPROCESSABLE_CONTENT,
					"OTP_INVALID", "The code is not correct.", Map.of("attempts_left", wrong.attemptsLeft()));
		};
	}

	@PostMapping("/session/refresh")
	TokenResponse refresh(@RequestBody RefreshRequest body) {
		return sessions.refresh(body == null ? null : body.refreshToken(), SessionKind.PARTNER_APPLICANT)
			.map(TokenResponse::of)
			.orElseThrow(() -> new ApiProblem(HttpStatus.UNAUTHORIZED, "SESSION_EXPIRED",
					"Your session has expired. Please sign in again."));
	}

	/** Step-up: a code to the applicant's own mobile before withdrawing. */
	@PostMapping("/reauth")
	ResponseEntity<Map<String, Object>> reauth(HttpServletRequest request) {
		Caller caller = CallerContext.require();
		reauth.sendCode(caller.subjectId(), request.getRemoteAddr());
		return ResponseEntity.accepted().body(Map.of("expires_in", properties.otpTtl().toSeconds(), "otp_length",
				properties.otpLength()));
	}

	@DeleteMapping("/session")
	ResponseEntity<Void> signOut() {
		Caller caller = CallerContext.require();
		signIn.signOut(caller.sessionId(), caller.subjectId());
		return ResponseEntity.noContent().build();
	}

	/** Opaque reference to the mobile an OTP was sent to: encrypted and time-stamped, never a lookup key. */
	private String otpRef(String mobile) {
		byte[] sealed = cipher.encryptBytes(
				(mobile + "|" + Instant.now(clock).getEpochSecond()).getBytes(StandardCharsets.UTF_8));
		return OTP_REF_PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(sealed);
	}

	private String mobileFromRef(String ref) {
		if (ref == null || !ref.startsWith(OTP_REF_PREFIX)) {
			throw invalidRef();
		}
		String[] parts;
		try {
			parts = new String(cipher.decryptBytes(Base64.getUrlDecoder().decode(ref.substring(OTP_REF_PREFIX.length()))),
					StandardCharsets.UTF_8).split("\\|");
		}
		catch (RuntimeException ex) {
			throw invalidRef();
		}
		if (parts.length != 2 || !parts[1].matches("\\d+")) {
			throw invalidRef();
		}
		if (Instant.ofEpochSecond(Long.parseLong(parts[1])).plus(properties.otpTtl()).isBefore(Instant.now(clock))) {
			throw expired();
		}
		return parts[0];
	}

	private static ApiProblem expired() {
		return new ApiProblem(HttpStatus.GONE, "OTP_EXPIRED", "This code has expired. Please request a new one.");
	}

	private static ValidationException invalidRef() {
		return ValidationException.field("otp_ref", "Please request a new code");
	}
}
