package in.eko.eloka.partner.shared.logging;

import org.springframework.boot.json.JsonWriter;
import org.springframework.boot.logging.structured.StructuredLoggingJsonMembersCustomizer;

/**
 * Applies {@link PiiMasker} to every string value of JSON (structured) log lines, the
 * production equivalent of {@code %maskedMsg}. Wired by
 * {@code logging.structured.json.customizer} in application-prod.yaml.
 */
public class MaskingJsonCustomizer implements StructuredLoggingJsonMembersCustomizer<Object> {

	@Override
	public void customize(JsonWriter.Members<Object> members) {
		members.applyingValueProcessor(JsonWriter.ValueProcessor.of(String.class, PiiMasker::mask));
	}
}
