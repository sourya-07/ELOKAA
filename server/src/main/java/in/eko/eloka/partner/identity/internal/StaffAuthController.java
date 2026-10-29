package in.eko.eloka.partner.identity.internal;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import com.fasterxml.jackson.annotation.JsonProperty;
import in.eko.eloka.partner.identity.StaffView;
import in.eko.eloka.partner.shared.config.ElokaProperties;
import in.eko.eloka.partner.shared.error.ApiProblem;
import in.eko.eloka.partner.shared.error.ForbiddenException;
import in.eko.eloka.partner.shared.security.Caller;
import in.eko.eloka.partner.shared.security.Role;
import in.eko.eloka.partner.shared.security.CallerContext;
import in.eko.eloka.partner.shared.security.SessionKind;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Staff sign-in for the review console. Unknown and disabled accounts get the same answer. */
@RestController
@RequestMapping("/v1/admin")
class StaffAuthController {

	private final StaffService staff;
	private final SessionService sessions;
	private final ElokaProperties eloka;

	/**
	 * Who is signed in, and where: {@code test_providers} is true only where fake identity checks may
	 * run (local and test), so the console can say that evidence there is not real.
	 */
	record Me(UUID id, @JsonProperty("email_masked") String emailMasked, @JsonProperty("display_name") String displayName,
			Set<Role> roles, boolean active, String environment, @JsonProperty("test_providers") boolean testProviders) {
	}

	StaffAuthController(StaffService staff, SessionService sessions, ElokaProperties eloka) {
		this.eloka = eloka;
		this.staff = staff;
		this.sessions = sessions;
	}

	record GoogleRequest(@JsonProperty("id_token") String idToken) {
	}

	record OtpRequest(String mobile, String otp) {
	}

	record RefreshRequest(@JsonProperty("refresh_token") String refreshToken) {
	}

	record SignedIn(@JsonProperty("access_token") String accessToken, @JsonProperty("refresh_token") String refreshToken,
			@JsonProperty("token_type") String tokenType, @JsonProperty("expires_in") long expiresIn, StaffView staff) {
	}

	record Tokens(@JsonProperty("access_token") String accessToken, @JsonProperty("refresh_token") String refreshToken,
			@JsonProperty("token_type") String tokenType, @JsonProperty("expires_in") long expiresIn) {
	}

	/** Production path: a Google ID token from the console's Google sign-in button. */
	@PostMapping("/auth/google")
	SignedIn google(@RequestBody GoogleRequest body) {
		return respond(staff.googleSignIn(body == null ? null : body.idToken()));
	}

	/** Local/test only (404 elsewhere). */
	@PostMapping("/auth/otp")
	ResponseEntity<Map<String, Object>> sendOtp(@RequestBody OtpRequest body, HttpServletRequest request) {
		staff.sendOtp(body == null ? null : body.mobile(), request.getRemoteAddr());
		return ResponseEntity.accepted().body(Map.of("otp_length", 6));
	}

	@PostMapping("/auth/otp/verify")
	SignedIn verifyOtp(@RequestBody OtpRequest body) {
		return respond(staff.verifyOtp(body == null ? null : body.mobile(), body == null ? null : body.otp()));
	}

	@PostMapping("/auth/refresh")
	Tokens refresh(@RequestBody RefreshRequest body) {
		return sessions.refresh(body == null ? null : body.refreshToken(), SessionKind.STAFF)
			.map(tokens -> new Tokens(tokens.accessToken(), tokens.refreshToken(), "Bearer", tokens.expiresIn().toSeconds()))
			.orElseThrow(() -> new ApiProblem(HttpStatus.UNAUTHORIZED, "SESSION_EXPIRED",
					"Your session has expired. Please sign in again."));
	}

	@DeleteMapping("/auth/session")
	ResponseEntity<Void> signOut() {
		Caller caller = requireStaff();
		sessions.revoke(caller.sessionId(), "SIGN_OUT");
		return ResponseEntity.noContent().build();
	}

	/** Who am I and what may I do (the console hides actions the staff member cannot take). */
	@GetMapping("/me")
	Me me() {
		Caller caller = requireStaff();
		StaffView view = staff.find(caller.subjectId()).orElseThrow(() -> new ForbiddenException("FORBIDDEN", "Not allowed"));
		return new Me(view.id(), view.emailMasked(), view.displayName(), view.roles(), view.active(),
				eloka.environment().name().toLowerCase(java.util.Locale.ROOT), eloka.environment().allowsDevelopmentShortcuts());
	}

	private static Caller requireStaff() {
		Caller caller = CallerContext.require();
		if (caller.kind() != SessionKind.STAFF) {
			throw new ForbiddenException("FORBIDDEN", "You are not allowed to do this.");
		}
		return caller;
	}

	private static SignedIn respond(StaffService.SignIn result) {
		return switch (result) {
			case StaffService.SignIn.SignedIn signedIn -> new SignedIn(signedIn.tokens().accessToken(),
					signedIn.tokens().refreshToken(), "Bearer", signedIn.tokens().expiresIn().toSeconds(), signedIn.staff());
			case StaffService.SignIn.NotAllowed notAllowed -> throw new ApiProblem(HttpStatus.FORBIDDEN, "NOT_STAFF",
					"This account cannot sign in to the review console.");
			case StaffService.SignIn.Expired expired -> throw new ApiProblem(HttpStatus.GONE, "OTP_EXPIRED",
					"This code has expired. Please request a new one.");
			case StaffService.SignIn.WrongCode wrong when wrong.attemptsLeft() == 0 -> throw new ApiProblem(
					HttpStatus.TOO_MANY_REQUESTS, "OTP_LOCKED", "Too many wrong attempts. Please request a new code.");
			case StaffService.SignIn.WrongCode wrong -> throw new ApiProblem(HttpStatus.UNPROCESSABLE_CONTENT,
					"OTP_INVALID", "The code is not correct.", Map.of("attempts_left", wrong.attemptsLeft()));
		};
	}
}
