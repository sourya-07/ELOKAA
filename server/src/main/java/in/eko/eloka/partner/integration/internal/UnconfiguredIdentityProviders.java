package in.eko.eloka.partner.integration.internal;

import java.time.LocalDate;
import in.eko.eloka.partner.integration.CreditBureau;
import in.eko.eloka.partner.integration.DigiLockerProvider;
import in.eko.eloka.partner.integration.PanVerifier;
import in.eko.eloka.partner.integration.ProviderNotConfiguredException;

/** No provider in this environment: every call is refused, never passed. */
final class UnconfiguredIdentityProviders {

	private UnconfiguredIdentityProviders() {
	}

	static final class Pan implements PanVerifier {

		@Override
		public PanCheck verify(String pan, String name, LocalDate dob) {
			throw new ProviderNotConfiguredException("PAN verification");
		}
	}

	static final class DigiLocker implements DigiLockerProvider {

		@Override
		public Session start(String verificationId, String returnUrl) {
			throw new ProviderNotConfiguredException("DigiLocker");
		}

		@Override
		public Fetch fetch(String verificationId, String referenceId) {
			throw new ProviderNotConfiguredException("DigiLocker");
		}
	}

	static final class Bureau implements CreditBureau {

		@Override
		public Report fetchScore(Request request) {
			throw new ProviderNotConfiguredException("Credit check");
		}
	}
}
