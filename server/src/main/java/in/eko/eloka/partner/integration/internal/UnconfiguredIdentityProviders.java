package in.eko.eloka.partner.integration.internal;

import java.time.LocalDate;
import in.eko.eloka.partner.integration.CreditBureau;
import in.eko.eloka.partner.integration.DigiLockerProvider;
import in.eko.eloka.partner.integration.DrivingLicenceVerifier;
import in.eko.eloka.partner.integration.IdDocumentCheck;
import in.eko.eloka.partner.integration.NameMatcher;
import in.eko.eloka.partner.integration.PanVerifier;
import in.eko.eloka.partner.integration.ProviderNotConfiguredException;
import in.eko.eloka.partner.integration.VoterIdVerifier;

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

	static final class VoterId implements VoterIdVerifier {

		@Override
		public IdDocumentCheck verify(String epicNumber) {
			throw new ProviderNotConfiguredException("Voter ID verification");
		}
	}

	static final class DrivingLicence implements DrivingLicenceVerifier {

		@Override
		public IdDocumentCheck verify(String licenceNumber, LocalDate dob) {
			throw new ProviderNotConfiguredException("Driving licence verification");
		}
	}

	static final class Names implements NameMatcher {

		@Override
		public double score(String name1, String name2) {
			throw new ProviderNotConfiguredException("Name match");
		}
	}
}
