package in.eko.eloka.partner.onboarding;

import java.util.UUID;

/** An application was submitted (or resubmitted after corrections) and waits for review. */
public record ApplicationSubmitted(UUID applicationId, String publicId) {
}
