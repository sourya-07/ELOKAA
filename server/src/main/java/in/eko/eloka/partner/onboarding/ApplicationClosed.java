package in.eko.eloka.partner.onboarding;

import java.util.UUID;

/** An application reached a terminal status. Listeners release what it held and plan data deletion. */
public record ApplicationClosed(UUID applicationId, ApplicationStatus status) {
}
