package in.eko.eloka.partner.integration.internal;

import java.util.UUID;
import in.eko.eloka.partner.integration.MobileOtpGateway;
import in.eko.eloka.partner.shared.config.UnsafeInProduction;

/** Local and test only: no SMS is sent and the configured fixed code is accepted. */
class FakeMobileOtpGateway implements MobileOtpGateway, UnsafeInProduction {

	private final String code;

	FakeMobileOtpGateway(String code) {
		this.code = code;
	}

	@Override
	public SentOtp send(String mobile) {
		return new SentOtp("fake-" + UUID.randomUUID(), null);
	}

	@Override
	public boolean verify(String mobile, String reference, String given) {
		return code.equals(given);
	}

	@Override
	public String unsafeReason() {
		return "fake mobile OTP provider is active (fixed code, no SMS)";
	}
}
