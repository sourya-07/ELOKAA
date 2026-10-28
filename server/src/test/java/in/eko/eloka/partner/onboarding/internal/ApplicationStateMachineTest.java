package in.eko.eloka.partner.onboarding.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;
import in.eko.eloka.partner.audit.AuditLog;
import in.eko.eloka.partner.onboarding.ApplicationClosed;
import in.eko.eloka.partner.onboarding.ApplicationStatus;
import in.eko.eloka.partner.onboarding.ApplicationSubmitted;
import in.eko.eloka.partner.shared.error.ApiProblem;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

/** Every (status, event) pair: allowed ones move to the documented status, all others are refused. */
class ApplicationStateMachineTest {

	private static final Map<String, ApplicationStatus> ALLOWED = Map.ofEntries(
			Map.entry("IN_PROGRESS/MARK_INELIGIBLE", ApplicationStatus.INELIGIBLE),
			Map.entry("CORRECTION_REQUIRED/MARK_INELIGIBLE", ApplicationStatus.INELIGIBLE),
			Map.entry("IN_PROGRESS/SUBMIT", ApplicationStatus.SUBMITTED),
			Map.entry("CORRECTION_REQUIRED/SUBMIT", ApplicationStatus.SUBMITTED),
			Map.entry("IN_PROGRESS/WITHDRAW", ApplicationStatus.WITHDRAWN),
			Map.entry("SUBMITTED/WITHDRAW", ApplicationStatus.WITHDRAWN),
			Map.entry("CORRECTION_REQUIRED/WITHDRAW", ApplicationStatus.WITHDRAWN),
			Map.entry("IN_PROGRESS/EXPIRE", ApplicationStatus.EXPIRED),
			Map.entry("CORRECTION_REQUIRED/EXPIRE", ApplicationStatus.EXPIRED),
			Map.entry("SUBMITTED/CLAIM", ApplicationStatus.UNDER_REVIEW),
			Map.entry("UNDER_REVIEW/RELEASE", ApplicationStatus.SUBMITTED),
			Map.entry("UNDER_REVIEW/REQUEST_CORRECTION", ApplicationStatus.CORRECTION_REQUIRED),
			Map.entry("UNDER_REVIEW/HOLD", ApplicationStatus.ON_HOLD),
			Map.entry("ON_HOLD/RELEASE_HOLD", ApplicationStatus.UNDER_REVIEW),
			Map.entry("UNDER_REVIEW/APPROVE", ApplicationStatus.APPROVED),
			Map.entry("UNDER_REVIEW/REJECT", ApplicationStatus.REJECTED),
			Map.entry("ON_HOLD/REJECT", ApplicationStatus.REJECTED));

	private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
	private final ApplicationStateMachine machine = new ApplicationStateMachine(mock(TimelineEntryRepository.class),
			mock(AuditLog.class), events, Clock.fixed(Instant.parse("2026-10-27T10:00:00Z"), ZoneOffset.UTC));

	@Test
	void transitionTableMatchesTheJourneyDocument() {
		for (ApplicationStatus from : ApplicationStatus.values()) {
			for (LifecycleEvent event : LifecycleEvent.values()) {
				PartnerApplication application = applicationIn(from);
				ApplicationStatus expected = ALLOWED.get(from + "/" + event);
				if (expected == null) {
					assertThat(machine.allows(application, event)).as(from + "/" + event).isFalse();
					assertThatThrownBy(() -> machine.apply(application, event, "SYSTEM", null))
						.isInstanceOf(ApiProblem.class)
						.hasMessageContaining("not possible");
				}
				else {
					assertThat(machine.apply(application, event, "SYSTEM", null)).as(from + "/" + event)
						.isEqualTo(expected);
					assertThat(application.getStatus()).isEqualTo(expected);
				}
			}
		}
	}

	@Test
	void terminalStatusesHaveNoWayOut() {
		for (ApplicationStatus from : ApplicationStatus.values()) {
			if (from.isTerminal()) {
				for (LifecycleEvent event : LifecycleEvent.values()) {
					assertThat(machine.allows(applicationIn(from), event)).isFalse();
				}
			}
		}
	}

	@Test
	void submittingAndClosingPublishEvents() {
		machine.apply(applicationIn(ApplicationStatus.IN_PROGRESS), LifecycleEvent.SUBMIT, "APPLICANT", "a");
		verify(events).publishEvent(any(ApplicationSubmitted.class));
		verify(events, never()).publishEvent(any(ApplicationClosed.class));
		machine.apply(applicationIn(ApplicationStatus.IN_PROGRESS), LifecycleEvent.MARK_INELIGIBLE, "SYSTEM", null);
		verify(events).publishEvent(any(ApplicationClosed.class));
	}

	private static PartnerApplication applicationIn(ApplicationStatus status) {
		PartnerApplication application = new PartnerApplication("ptn_TEST", UUID.randomUUID(),
				Instant.parse("2026-10-27T09:00:00Z"), Duration.ofDays(30));
		ReflectionTestUtils.setField(application, "status", status);
		return application;
	}
}
