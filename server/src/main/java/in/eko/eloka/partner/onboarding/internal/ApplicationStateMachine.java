package in.eko.eloka.partner.onboarding.internal;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import in.eko.eloka.partner.audit.AuditEntry;
import in.eko.eloka.partner.audit.AuditLog;
import in.eko.eloka.partner.onboarding.ApplicationClosed;
import in.eko.eloka.partner.onboarding.ApplicationStatus;
import in.eko.eloka.partner.onboarding.ApplicationSubmitted;
import in.eko.eloka.partner.shared.error.ApiProblem;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * The only way an application changes status. Callers hold the row lock; this checks the
 * {@code (from, event)} pair, moves the status and records the change in the audit log and the
 * applicant timeline in the same transaction. Guards (e.g. "all steps done") belong to the caller.
 */
@Component
class ApplicationStateMachine {

	private record Key(ApplicationStatus from, LifecycleEvent event) {
	}

	private static final Map<Key, ApplicationStatus> TRANSITIONS = Map.ofEntries(
			Map.entry(new Key(ApplicationStatus.IN_PROGRESS, LifecycleEvent.MARK_INELIGIBLE), ApplicationStatus.INELIGIBLE),
			// A PAN corrected after review runs the credit check again
			Map.entry(new Key(ApplicationStatus.CORRECTION_REQUIRED, LifecycleEvent.MARK_INELIGIBLE), ApplicationStatus.INELIGIBLE),
			Map.entry(new Key(ApplicationStatus.IN_PROGRESS, LifecycleEvent.SUBMIT), ApplicationStatus.SUBMITTED),
			Map.entry(new Key(ApplicationStatus.CORRECTION_REQUIRED, LifecycleEvent.SUBMIT), ApplicationStatus.SUBMITTED),
			Map.entry(new Key(ApplicationStatus.IN_PROGRESS, LifecycleEvent.WITHDRAW), ApplicationStatus.WITHDRAWN),
			Map.entry(new Key(ApplicationStatus.SUBMITTED, LifecycleEvent.WITHDRAW), ApplicationStatus.WITHDRAWN),
			Map.entry(new Key(ApplicationStatus.CORRECTION_REQUIRED, LifecycleEvent.WITHDRAW), ApplicationStatus.WITHDRAWN),
			Map.entry(new Key(ApplicationStatus.IN_PROGRESS, LifecycleEvent.EXPIRE), ApplicationStatus.EXPIRED),
			Map.entry(new Key(ApplicationStatus.CORRECTION_REQUIRED, LifecycleEvent.EXPIRE), ApplicationStatus.EXPIRED),
			Map.entry(new Key(ApplicationStatus.SUBMITTED, LifecycleEvent.CLAIM), ApplicationStatus.UNDER_REVIEW),
			Map.entry(new Key(ApplicationStatus.UNDER_REVIEW, LifecycleEvent.RELEASE), ApplicationStatus.SUBMITTED),
			Map.entry(new Key(ApplicationStatus.UNDER_REVIEW, LifecycleEvent.REQUEST_CORRECTION), ApplicationStatus.CORRECTION_REQUIRED),
			Map.entry(new Key(ApplicationStatus.UNDER_REVIEW, LifecycleEvent.HOLD), ApplicationStatus.ON_HOLD),
			Map.entry(new Key(ApplicationStatus.ON_HOLD, LifecycleEvent.RELEASE_HOLD), ApplicationStatus.UNDER_REVIEW),
			Map.entry(new Key(ApplicationStatus.UNDER_REVIEW, LifecycleEvent.APPROVE), ApplicationStatus.APPROVED),
			Map.entry(new Key(ApplicationStatus.UNDER_REVIEW, LifecycleEvent.REJECT), ApplicationStatus.REJECTED),
			Map.entry(new Key(ApplicationStatus.ON_HOLD, LifecycleEvent.REJECT), ApplicationStatus.REJECTED));

	private final TimelineEntryRepository timeline;
	private final AuditLog audit;
	private final ApplicationEventPublisher events;
	private final Clock clock;

	ApplicationStateMachine(TimelineEntryRepository timeline, AuditLog audit, ApplicationEventPublisher events,
			Clock clock) {
		this.timeline = timeline;
		this.audit = audit;
		this.events = events;
		this.clock = clock;
	}

	/** @return true when {@code event} is allowed from the current status */
	boolean allows(PartnerApplication application, LifecycleEvent event) {
		return TRANSITIONS.containsKey(new Key(application.getStatus(), event));
	}

	/**
	 * @param actorType {@code APPLICANT}, {@code STAFF} or {@code SYSTEM}
	 * @param actorId applicant or staff id ({@code null} for the system); kept in the audit log only
	 */
	ApplicationStatus apply(PartnerApplication application, LifecycleEvent event, String actorType, String actorId) {
		ApplicationStatus from = application.getStatus();
		ApplicationStatus to = TRANSITIONS.get(new Key(from, event));
		if (to == null) {
			throw new ApiProblem(HttpStatus.CONFLICT, "INVALID_STATE_TRANSITION",
					"This is not possible while the application is " + from.name().toLowerCase().replace('_', ' '),
					Map.of("current_status", from.name()));
		}
		Instant now = Instant.now(clock);
		application.moveTo(to, now);
		timeline.save(new TimelineEntry(application.getId(), "STATUS_" + to.name(), null, from, to, actorType, now));
		audit.record(new AuditEntry("APPLICATION_" + event.name(), actorType, actorId, "PARTNER_APPLICATION",
				application.getPublicId(), Map.of("from", from.name(), "to", to.name())));
		if (to == ApplicationStatus.SUBMITTED) {
			events.publishEvent(new ApplicationSubmitted(application.getId(), application.getPublicId()));
		}
		if (to.isTerminal()) {
			events.publishEvent(new ApplicationClosed(application.getId(), to));
		}
		return to;
	}
}
