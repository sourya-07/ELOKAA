package in.eko.eloka.partner.integration.internal;

import java.util.Optional;
import in.eko.eloka.partner.integration.ReverseGeocoder;

/** No address lookup configured: the live photo is kept with its coordinates only. */
class UnconfiguredReverseGeocoder implements ReverseGeocoder {

	@Override
	public Optional<String> addressOf(double latitude, double longitude) {
		return Optional.empty();
	}
}
