package in.eko.eloka.partner.integration.internal;

import in.eko.eloka.partner.integration.EmailSender;
import in.eko.eloka.partner.shared.config.UnsafeInProduction;

/** Local and test only: sends nothing (with the fixed code, the applicant types the known value). */
class FakeEmailSender implements EmailSender, UnsafeInProduction {

	@Override
	public void send(String to, String subject, String text) {
		// deliberately nothing: no mail, and the text (it holds a code) is never logged
	}

	@Override
	public String unsafeReason() {
		return "fake email sender is active (no email is sent)";
	}
}
