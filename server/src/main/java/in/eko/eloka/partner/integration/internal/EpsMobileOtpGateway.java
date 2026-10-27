package in.eko.eloka.partner.integration.internal;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import in.eko.eloka.partner.integration.MobileOtpGateway;
import in.eko.eloka.partner.integration.ProviderUnavailableException;
import org.springframework.http.HttpMethod;
import tools.jackson.databind.JsonNode;

/**
 * EPS Mobile/OTP Verification: Send OTP ({@code POST /tools/kyc/mobile/otp}) and Verify OTP
 * ({@code PUT /tools/kyc/mobile/otp/verify}). Docs: https://eps.eko.in/docs/mobile-otp-send.
 * EPS answers {@code status 0} on success and {@code status 1} for a wrong or expired code.
 */
class EpsMobileOtpGateway implements MobileOtpGateway {

	/**
	 * EPS sends e.g. {@code Fri May 22 16:04:04 IST 2026}. "IST" is ambiguous to Java's zone parser
	 * (it is also Irish and Israeli time), so only that exact form is accepted, as India time;
	 * anything else falls back to our own expiry.
	 */
	private static final Pattern EXPIRY_IST = Pattern.compile("(\\w{3} \\w{3} \\d{2} \\d{2}:\\d{2}:\\d{2}) IST (\\d{4})");
	private static final DateTimeFormatter EXPIRY = DateTimeFormatter.ofPattern("EEE MMM dd HH:mm:ss yyyy", Locale.ENGLISH);
	private static final ZoneId INDIA = ZoneId.of("Asia/Kolkata");

	private final EpsClient eps;
	private final String cspId;

	EpsMobileOtpGateway(EpsClient eps, String cspId) {
		this.eps = eps;
		this.cspId = cspId == null || cspId.isBlank() ? eps.initiatorId() : cspId;
	}

	@Override
	public SentOtp send(String mobile) {
		JsonNode response = eps.call("mobile-otp-send", HttpMethod.POST, "/tools/kyc/mobile/otp",
				Map.of("mobile", mobile, "csp_id", cspId, "client_ref_id", EpsClient.clientRef()));
		JsonNode data = response.path("data");
		String reference = data.path("tid").asString("");
		if (response.get("status").asInt() != 0 || reference.isBlank()) {
			throw new ProviderUnavailableException("Mobile verification");
		}
		return new SentOtp(reference, expiry(data.path("otp_expiry_timestamp").asString("")));
	}

	@Override
	public boolean verify(String mobile, String reference, String code) {
		JsonNode response = eps.call("mobile-otp-verify", HttpMethod.PUT, "/tools/kyc/mobile/otp/verify",
				Map.of("mobile", mobile, "otp", code, "otp_ref_id", reference, "client_ref_id", EpsClient.clientRef()));
		if (response.get("status").asInt() != 0) {
			return false;
		}
		// Trust a success only when EPS confirms the same mobile it was asked about.
		return mobile.equals(response.path("data").path("mobile").asString(""));
	}

	private static Instant expiry(String text) {
		Matcher matcher = EXPIRY_IST.matcher(text.trim());
		if (!matcher.matches()) {
			return null;
		}
		try {
			return LocalDateTime.parse(matcher.group(1) + " " + matcher.group(2), EXPIRY).atZone(INDIA).toInstant();
		}
		catch (DateTimeParseException ex) {
			return null;
		}
	}

}
