package in.eko.eloka.partner.shared.logging;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Last line of defence: redacts personal data that slipped into a log message.
 * The first line is never logging request bodies at all.
 * Mobiles are matched before Aadhaar so {@code +91XXXXXXXXXX} is not mistaken for a 12-digit Aadhaar.
 * Patterns deliberately avoid {@code \b} (an underscore counts as a word character, so
 * {@code \b} misses values glued to {@code _}).
 */
public final class PiiMasker {

	private record Rule(Pattern pattern, String replacement) {
	}

	private static final List<Rule> RULES = List.of(
			new Rule(Pattern.compile("(?i)(bearer\\s+)[A-Za-z0-9._~+/=-]+"), "$1[REDACTED]"),
			new Rule(Pattern.compile("ptn_(at|rt)_[A-Za-z0-9_-]+"), "ptn_$1_[REDACTED]"),
			new Rule(Pattern.compile("(?i)(\"?(?:otp|id_token|password|secret|access_token|refresh_token|credit_score|aadhaar)[a-z_]*\"?\\s*[:=]\\s*\"?)[^\",&\\s}]+"),
					"$1[REDACTED]"),
			new Rule(Pattern.compile("(?<![A-Za-z0-9])[A-Z]{5}[0-9]{4}[A-Z](?![A-Za-z0-9])"), "[PAN]"),
			// Voter ID (EPIC): three letters and seven digits, in either case
			new Rule(Pattern.compile("(?i)(?<![A-Za-z0-9])[A-Z]{3}[0-9]{7}(?![A-Za-z0-9])"), "[EPIC]"),
			// Email: the first character and the domain stay, so a log line still says which provider
			new Rule(Pattern.compile("(?<![A-Za-z0-9._%+-])([A-Za-z0-9])[A-Za-z0-9._%+-]*@([A-Za-z0-9-]+(?:\\.[A-Za-z0-9-]+)+)"), "$1***@$2"),
			new Rule(Pattern.compile("(?<!\\d)(?:\\+?91)?[6-9]\\d{5}(\\d{4})(?!\\d)"), "******$1"),
			new Rule(Pattern.compile("(?<!\\d)\\d{4}[ -]?\\d{4}[ -]?\\d{4}(?!\\d)"), "[AADHAAR]"),
			new Rule(Pattern.compile("(?<!\\d)\\d{9,18}(?!\\d)"), "[NUMBER]"));

	private PiiMasker() {
	}

	public static String mask(String message) {
		if (message == null || message.isEmpty()) {
			return message;
		}
		String result = message;
		for (Rule rule : RULES) {
			Matcher matcher = rule.pattern().matcher(result);
			result = matcher.replaceAll(rule.replacement());
		}
		return result;
	}
}
