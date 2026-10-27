package in.eko.eloka.partner.identity;

import java.util.UUID;

/** Step-up for sensitive applicant actions (withdrawing): a fresh OTP to the applicant's own mobile. */
public interface ApplicantReauth {

	/** Sends a code to the mobile the applicant signed in with. */
	void sendCode(UUID applicantId, String clientIp);

	/**
	 * @return true once for a correct, live code
	 * @throws in.eko.eloka.partner.shared.error.ApiProblem {@code OTP_INVALID} (with attempts left),
	 *         {@code OTP_LOCKED} or {@code OTP_EXPIRED}
	 */
	boolean verify(UUID applicantId, String code);
}
