package in.eko.eloka.partner.shared.crypto;

import in.eko.eloka.partner.shared.error.ValidationException;

/** Normalises Indian mobile numbers to the 10-digit form used everywhere in the app. */
public final class Mobiles {

	private Mobiles() {
	}

	/**
	 * @param raw input such as {@code "+91 98765 43210"}, {@code "098765 43210"} or {@code "9876543210"}
	 * @return ten digits starting with 6-9
	 * @throws ValidationException when it is not a valid Indian mobile number
	 */
	public static String normalize(String raw) {
		String digits = raw == null ? "" : raw.replaceAll("\\D", "");
		if (digits.length() == 12 && digits.startsWith("91")) {
			digits = digits.substring(2);
		}
		else if (digits.length() == 11 && digits.startsWith("0")) {
			digits = digits.substring(1); // domestic trunk prefix
		}
		if (!digits.matches("[6-9]\\d{9}")) {
			throw ValidationException.field("mobile", "Enter a valid 10-digit mobile number");
		}
		return digits;
	}
}
