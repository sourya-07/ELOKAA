package in.eko.eloka.partner.shared.crypto;

/** Display masks for personal data. Used in API responses for staff and in logs. */
public final class Masking {

	private Masking() {
	}

	/** {@code 9876543210 → ******3210} */
	public static String mobile(String mobile) {
		return tail(mobile, 4, '*');
	}

	/** {@code ABCDE1234F → ABCDE****F} */
	public static String pan(String pan) {
		if (pan == null || pan.length() != 10) {
			return pan == null ? null : "**********";
		}
		return pan.substring(0, 5) + "****" + pan.charAt(9);
	}

	/** {@code 123456789012 → XXXXXXXX9012} */
	public static String account(String account) {
		return tail(account, 4, 'X');
	}

	/** {@code ravi@example.com → r***@example.com} */
	public static String email(String email) {
		if (email == null || !email.contains("@")) {
			return email;
		}
		int at = email.indexOf('@');
		return email.charAt(0) + "***" + email.substring(at);
	}

	private static String tail(String value, int keep, char mask) {
		if (value == null) {
			return null;
		}
		if (value.length() <= keep) {
			return String.valueOf(mask).repeat(value.length());
		}
		return String.valueOf(mask).repeat(value.length() - keep) + value.substring(value.length() - keep);
	}
}
