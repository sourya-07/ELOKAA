package in.eko.eloka.partner.integration;

import java.util.Optional;

/**
 * An approximate address for a point (the live photo's browser-reported location). Never a checked
 * address, and never required: an unknown point, a slow or failed lookup, or no geocoder configured all
 * answer empty, and the upload goes on without an address.
 */
public interface ReverseGeocoder {

	Optional<String> addressOf(double latitude, double longitude);
}
