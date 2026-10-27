package in.eko.eloka.partner.integration.internal;

import java.time.LocalDate;
import java.util.UUID;
import in.eko.eloka.partner.integration.CreditBureau;
import in.eko.eloka.partner.integration.DigiLockerProvider;
import in.eko.eloka.partner.integration.PanVerifier;
import in.eko.eloka.partner.integration.ProviderUnavailableException;
import in.eko.eloka.partner.shared.config.UnsafeInProduction;

/**
 * Local and test stand-ins. Their answers depend on the input so every branch can be tried by
 * hand (docs/LOCAL_DEVELOPMENT.md):
 * <ul>
 * <li>PAN: digits {@code 0000} → invalid, {@code 1111} → name mismatch, {@code 2222} → DOB mismatch,
 * {@code 9999} → provider down, anything else → match.</li>
 * <li>Credit score (by PAN digits): {@code 6990} → 699, {@code 7000} → 700, {@code 0001} → no record,
 * {@code 9997} → 950 (not a valid score), {@code 9998} → bureau down, anything else → 742.</li>
 * <li>DigiLocker: returns at once with the sample person RAVI SAMPLE.</li>
 * </ul>
 */
final class FakeIdentityProviders {

	private FakeIdentityProviders() {
	}

	static String digits(String pan) {
		return pan == null || pan.length() != 10 ? "" : pan.substring(5, 9);
	}

	static final class Pan implements PanVerifier, UnsafeInProduction {

		@Override
		public PanCheck verify(String pan, String name, LocalDate dob) {
			Result result = switch (digits(pan)) {
				case "0000" -> Result.INVALID_PAN;
				case "1111" -> Result.NAME_MISMATCH;
				case "2222" -> Result.DOB_MISMATCH;
				case "9999" -> throw new ProviderUnavailableException("PAN verification");
				default -> Result.MATCH;
			};
			return new PanCheck(result, true, "fake-pan-" + UUID.randomUUID());
		}

		@Override
		public String unsafeReason() {
			return "fake PAN verifier is active";
		}
	}

	static final class DigiLocker implements DigiLockerProvider, UnsafeInProduction {

		@Override
		public Session start(String verificationId, String returnUrl) {
			return new Session("fake-dl-" + verificationId, returnUrl); // "comes back" at once
		}

		@Override
		public Fetch fetch(String verificationId, String referenceId) {
			if (!referenceId.equals("fake-dl-" + verificationId)) {
				return new Fetch.Failed("INVALID_REFERENCE");
			}
			return new Fetch.Done(new Identity("RAVI SAMPLE", LocalDate.of(1990, 1, 15), "1234",
					new Address("Flat 12", "Sample Lane, Bandra West", "Mumbai", "Mumbai Suburban", "Maharashtra",
							"400050")));
		}

		@Override
		public String unsafeReason() {
			return "fake DigiLocker is active";
		}
	}

	static final class Bureau implements CreditBureau, UnsafeInProduction {

		@Override
		public Report fetchScore(Request request) {
			String ref = "fake-cibil-" + UUID.randomUUID();
			return switch (digits(request.pan())) {
				case "6990" -> new Report(699, ref);
				case "7000" -> new Report(700, ref);
				case "0001" -> new Report(null, ref);
				case "9997" -> new Report(950, ref);
				case "9998" -> throw new ProviderUnavailableException("Credit check");
				default -> new Report(742, ref);
			};
		}

		@Override
		public String unsafeReason() {
			return "fake credit bureau is active";
		}
	}
}
