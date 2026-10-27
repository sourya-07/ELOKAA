package in.eko.eloka.partner.identity.internal;

import java.util.Map;
import java.util.UUID;
import in.eko.eloka.partner.identity.ApplicantReauth;
import in.eko.eloka.partner.shared.crypto.FieldCipher;
import in.eko.eloka.partner.shared.error.ApiProblem;
import in.eko.eloka.partner.shared.error.NotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/** Re-verification with an OTP to the applicant's own (stored, encrypted) mobile. */
@Service
class ApplicantReauthService implements ApplicantReauth {

	private final ApplicantRepository applicants;
	private final OtpService otps;
	private final FieldCipher cipher;

	ApplicantReauthService(ApplicantRepository applicants, OtpService otps, FieldCipher cipher) {
		this.applicants = applicants;
		this.otps = otps;
		this.cipher = cipher;
	}

	@Override
	public void sendCode(UUID applicantId, String clientIp) {
		otps.issue(OtpChallenge.PURPOSE_REAUTH, mobile(applicantId), clientIp);
	}

	@Override
	public boolean verify(UUID applicantId, String code) {
		return switch (otps.check(OtpChallenge.PURPOSE_REAUTH, mobile(applicantId), code)) {
			case OtpService.Check.Valid valid -> true;
			case OtpService.Check.NoLiveOtp none -> throw new ApiProblem(HttpStatus.GONE, "OTP_EXPIRED",
					"This code has expired. Please request a new one.");
			case OtpService.Check.Wrong wrong when wrong.attemptsLeft() == 0 -> throw new ApiProblem(
					HttpStatus.TOO_MANY_REQUESTS, "OTP_LOCKED", "Too many wrong attempts. Please request a new code.");
			case OtpService.Check.Wrong wrong -> throw new ApiProblem(HttpStatus.UNPROCESSABLE_CONTENT, "OTP_INVALID",
					"The code is not correct.", Map.of("attempts_left", wrong.attemptsLeft()));
		};
	}

	private String mobile(UUID applicantId) {
		return applicants.findById(applicantId)
			.map(applicant -> cipher.decrypt(applicant.getMobileEnc()))
			.orElseThrow(() -> new NotFoundException("APPLICANT_NOT_FOUND", "Applicant not found"));
	}
}
