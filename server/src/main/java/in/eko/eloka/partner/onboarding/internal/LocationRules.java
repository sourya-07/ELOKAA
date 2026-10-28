package in.eko.eloka.partner.onboarding.internal;

import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import in.eko.eloka.partner.shared.error.ApiProblem;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * The live photo's location: required, real numbers in range (never 0,0), an accuracy within
 * {@code max-accuracy-m}, and taken within a few minutes of the upload. A location less precise than
 * {@code flag-accuracy-m}, or outside India, is kept but flagged for staff (docs/OPEN_QUESTIONS.md Q-24).
 */
@Component
class LocationRules {

	/** What the client sent, as text (multipart fields); all null when it sent no location. */
	record Input(String latitude, String longitude, String accuracyM, String capturedAt) {
	}

	record Checked(double latitude, double longitude, double accuracyM, Instant capturedAt, String flags) {
	}

	private static final Duration CLOCK_SKEW = Duration.ofMinutes(5);

	private final double maxAccuracyM;
	private final double flagAccuracyM;

	LocationRules(@Value("${eloka.onboarding.location.max-accuracy-m:1000}") double maxAccuracyM,
			@Value("${eloka.onboarding.location.flag-accuracy-m:100}") double flagAccuracyM) {
		this.maxAccuracyM = maxAccuracyM;
		this.flagAccuracyM = flagAccuracyM;
	}

	Checked check(Input input, Instant now) {
		if (input == null || input.latitude() == null || input.longitude() == null || input.accuracyM() == null) {
			throw problem("LOCATION_REQUIRED", "The live photo needs your location. Allow location for this site and take the photo again.");
		}
		double lat = number(input.latitude());
		double lng = number(input.longitude());
		double accuracy = number(input.accuracyM());
		if (lat < -90 || lat > 90 || lng < -180 || lng > 180 || (lat == 0 && lng == 0) || accuracy <= 0) {
			throw problem("LOCATION_INVALID", "We could not read your location. Take the photo again.");
		}
		if (accuracy > maxAccuracyM) {
			throw problem("LOCATION_TOO_INACCURATE", "Your location is too rough (more than " + Math.round(maxAccuracyM)
					+ " m). Turn on precise location or GPS, then take the photo again.", Map.of("accuracy_m", Math.round(accuracy)));
		}
		Instant captured;
		try {
			captured = input.capturedAt() == null ? now : Instant.parse(input.capturedAt());
		}
		catch (DateTimeParseException ex) {
			throw problem("LOCATION_INVALID", "We could not read your location. Take the photo again.");
		}
		if (captured.isBefore(now.minus(CLOCK_SKEW)) || captured.isAfter(now.plus(CLOCK_SKEW))) {
			throw problem("LOCATION_STALE", "This location is not from now. Take the photo again.");
		}
		List<String> flags = new ArrayList<>();
		if (accuracy > flagAccuracyM) {
			flags.add("LOW_ACCURACY");
		}
		// A rough box around India, mainland and islands: a sign for staff, not a rule
		if (lat < 6 || lat > 37.6 || lng < 68 || lng > 97.5) {
			flags.add("OUTSIDE_INDIA");
		}
		return new Checked(lat, lng, accuracy, captured, String.join(",", flags));
	}

	private static double number(String value) {
		try {
			double parsed = Double.parseDouble(value.trim());
			if (Double.isNaN(parsed) || Double.isInfinite(parsed)) {
				throw problem("LOCATION_INVALID", "We could not read your location. Take the photo again.");
			}
			return parsed;
		}
		catch (NumberFormatException ex) {
			throw problem("LOCATION_INVALID", "We could not read your location. Take the photo again.");
		}
	}

	private static ApiProblem problem(String code, String detail) {
		return problem(code, detail, Map.of());
	}

	private static ApiProblem problem(String code, String detail, Map<String, Object> properties) {
		return new ApiProblem(HttpStatus.UNPROCESSABLE_CONTENT, code, detail, properties);
	}
}
