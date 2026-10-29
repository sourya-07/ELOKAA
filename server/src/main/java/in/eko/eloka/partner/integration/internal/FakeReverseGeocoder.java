package in.eko.eloka.partner.integration.internal;

import java.util.Locale;
import java.util.Optional;
import in.eko.eloka.partner.integration.ReverseGeocoder;
import in.eko.eloka.partner.shared.config.UnsafeInProduction;

/** Local and test only: an address that says it is made up, so nobody mistakes it for a real one. */
class FakeReverseGeocoder implements ReverseGeocoder, UnsafeInProduction {

	@Override
	public Optional<String> addressOf(double latitude, double longitude) {
		return Optional.of(String.format(Locale.ROOT, "Test address near %.4f, %.4f (no real lookup)", latitude, longitude));
	}

	@Override
	public String unsafeReason() {
		return "fake address lookup is active";
	}
}
