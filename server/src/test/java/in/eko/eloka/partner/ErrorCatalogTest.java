package in.eko.eloka.partner;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import in.eko.eloka.partner.verification.VerificationApi;
import org.junit.jupiter.api.Test;

/**
 * docs/ERROR_CATALOG.md lists exactly the codes the server can send: every code thrown in the source is
 * catalogued, and the catalog lists no code the server cannot send. Step results are built from the
 * verification enums, so a new result shows up here too.
 */
class ErrorCatalogTest {

	private static final Path CATALOG = Path.of("../docs/ERROR_CATALOG.md");

	/** Where an error code is written in the source: exception constructors, problem helpers, handler bodies. */
	private static final Pattern THROWN = Pattern.compile(
			"(?:ApiProblem\\(\\s*HttpStatus\\.[A-Z_]+,\\s*|Exception\\(\\s*|super\\(\\s*|problem\\(\\s*|limited\\(\\s*"
					+ "|\"code\",\\s*|SC_[A-Z_]+,\\s*)\"([A-Z][A-Z0-9]*(?:_[A-Z0-9]+)+|[A-Z]{4,})\"");

	/** The codes ApiExceptionHandler gives framework refusals (its frameworkCode switch). */
	private static final Pattern FRAMEWORK = Pattern.compile("(?:case \\d+ -> |[?:] )\"([A-Z_]+)\"");

	/** Literal step results passed to fail/failTechnically/block. */
	private static final Pattern STEP = Pattern.compile("(?:fail|failTechnically|block)\\(\\s*PartnerStep\\.[A-Z_]+,\\s*\"([A-Z_]+)\"");

	@Test
	void everyHttpErrorCodeIsCatalogued() throws IOException {
		Set<String> thrown = new TreeSet<>();
		try (Stream<Path> files = Files.walk(Path.of("src/main/java"))) {
			for (Path file : files.filter(f -> f.toString().endsWith(".java")).toList()) {
				String source = Files.readString(file);
				Matcher m = THROWN.matcher(source);
				while (m.find()) {
					thrown.add(m.group(1));
				}
				if (file.endsWith("ApiExceptionHandler.java")) {
					Matcher f = FRAMEWORK.matcher(source);
					while (f.find()) {
						thrown.add(f.group(1));
					}
				}
			}
		}
		// One-word matches are only codes when they are these; anything else is an ordinary word
		thrown.removeIf(code -> !code.contains("_") && !Set.of("CONFLICT", "FORBIDDEN", "UNAUTHENTICATED").contains(code));
		Set<String> catalogued = section("## HTTP errors");
		assertThat(catalogued).as("codes the server sends but docs/ERROR_CATALOG.md does not list").containsAll(thrown);
		assertThat(thrown).as("codes docs/ERROR_CATALOG.md lists but the server never sends").containsAll(catalogued);
	}

	@Test
	void everyStepResultIsCatalogued() throws IOException {
		Set<String> results = new TreeSet<>();
		try (Stream<Path> files = Files.walk(Path.of("src/main/java"))) {
			for (Path file : files.filter(f -> f.toString().endsWith(".java")).toList()) {
				Matcher m = STEP.matcher(Files.readString(file));
				while (m.find()) {
					// "DIGILOCKER_" + code is a prefix, expanded below
					if (!m.group(1).endsWith("_")) {
						results.add(m.group(1));
					}
				}
			}
		}
		// Built at run time: "PAN_" + result, "<type>_" + result, "DIGILOCKER_" + failure, the bureau's failure codes
		Arrays.stream(VerificationApi.PanOutcome.Result.values())
			.filter(r -> r != VerificationApi.PanOutcome.Result.MATCH)
			.forEach(r -> results.add("PAN_" + r.name()));
		results.add("PAN_ALREADY_USED");
		for (VerificationApi.AlternateType type : VerificationApi.AlternateType.values()) {
			Arrays.stream(VerificationApi.AlternateResult.values())
				.filter(r -> r != VerificationApi.AlternateResult.VERIFIED && r != VerificationApi.AlternateResult.UNAVAILABLE)
				.forEach(r -> results.add(type.name() + "_" + r.name()));
		}
		results.add("ID_ALREADY_USED");
		results.addAll(Set.of("DIGILOCKER_INVALID_REFERENCE", "DIGILOCKER_NO_AADHAAR_DATA"));
		results.addAll(Set.of("UNABLE_TO_ASSESS", "BUREAU_UNAVAILABLE", "NO_CREDIT_HISTORY", "INVALID_SCORE"));
		assertThat(section("## Step results")).isEqualTo(results);
	}

	/** The backticked codes in the first column of the table under a heading. */
	private static Set<String> section(String heading) throws IOException {
		String text = Files.readString(CATALOG);
		int start = text.indexOf(heading);
		assertThat(start).as("heading %s in %s", heading, CATALOG).isNotNegative();
		int end = text.indexOf("\n## ", start + heading.length());
		String body = end < 0 ? text.substring(start) : text.substring(start, end);
		Set<String> codes = new TreeSet<>();
		Matcher m = Pattern.compile("(?m)^\\| `([A-Z0-9_]+)` \\|").matcher(body);
		while (m.find()) {
			codes.add(m.group(1));
		}
		return codes;
	}
}
