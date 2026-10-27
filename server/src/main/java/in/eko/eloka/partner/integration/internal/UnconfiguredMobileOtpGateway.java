package in.eko.eloka.partner.integration.internal;

import in.eko.eloka.partner.integration.MobileOtpGateway;
import in.eko.eloka.partner.integration.ProviderNotConfiguredException;

/** No OTP provider in this environment: every call is refused. */
class UnconfiguredMobileOtpGateway implements MobileOtpGateway {

	private static final String NAME = "Mobile verification";

	@Override
	public SentOtp send(String mobile) {
		throw new ProviderNotConfiguredException(NAME);
	}

	@Override
	public boolean verify(String mobile, String reference, String code) {
		throw new ProviderNotConfiguredException(NAME);
	}
}
