package in.eko.eloka.partner.onboarding.internal;

import java.time.Instant;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import in.eko.eloka.partner.onboarding.ApplicationStatus;
import in.eko.eloka.partner.onboarding.PartnerStep;
import in.eko.eloka.partner.onboarding.StepStatus;
import in.eko.eloka.partner.shared.error.ApiProblem;
import org.springframework.http.HttpStatus;

/**
 * One write to an application, under its row lock. Gives step code what it needs and keeps the
 * step rules (prerequisites, dependency invalidation, timeline) in one place.
 */
final class StepContext {

	/** Reopen reason of a locked step whose input changed during a correction (not a reviewer's choice). */
	static final String DEPENDENCY_CHANGED = "DEPENDENCY_CHANGED";

	private final PartnerApplication application;
	private final Map<PartnerStep, ApplicationStep> steps = new EnumMap<>(PartnerStep.class);
	private final TimelineEntryRepository timeline;
	private final Instant now;

	StepContext(PartnerApplication application, List<ApplicationStep> stepRows, TimelineEntryRepository timeline,
			Instant now) {
		this.application = application;
		stepRows.forEach(step -> steps.put(step.getStepKey(), step));
		this.timeline = timeline;
		this.now = now;
	}

	PartnerApplication application() {
		return application;
	}

	UUID applicationId() {
		return application.getId();
	}

	Instant now() {
		return now;
	}

	ApplicationStep step(PartnerStep key) {
		return steps.get(key);
	}

	Map<PartnerStep, ApplicationStep> steps() {
		return steps;
	}

	/** Every prerequisite of {@code key} is satisfied, or {@code 409 PREREQUISITE_INCOMPLETE}. */
	void requirePrerequisites(PartnerStep key) {
		List<String> missing = key.prerequisites()
			.stream()
			.filter(prerequisite -> !steps.get(prerequisite).getStatus().isSatisfied())
			.map(Enum::name)
			.toList();
		if (!missing.isEmpty()) {
			throw new ApiProblem(HttpStatus.CONFLICT, "PREREQUISITE_INCOMPLETE", "Finish the earlier steps first",
					Map.of("missing_steps", missing));
		}
	}

	/** Marks the step done and resets the steps that were completed on the old data. */
	void complete(PartnerStep key, String summaryJson) {
		steps.get(key).complete(summaryJson, now);
		invalidateDependents(key, EnumSet.noneOf(PartnerStep.class));
		record("STEP_COMPLETED", key);
	}

	void fail(PartnerStep key, String errorCode) {
		steps.get(key).fail(errorCode);
		record("STEP_FAILED", key);
	}

	void failTechnically(PartnerStep key, String errorCode) {
		steps.get(key).failTechnically(errorCode);
		record("STEP_FAILED", key);
	}

	void awaitExternal(PartnerStep key) {
		steps.get(key).awaitExternal();
		record("STEP_PENDING_EXTERNAL", key);
	}

	void record(String eventType, PartnerStep key) {
		timeline.save(new TimelineEntry(application.getId(), eventType, key, null, null, "APPLICANT", now));
	}

	private void invalidateDependents(PartnerStep changed, Set<PartnerStep> visited) {
		for (PartnerStep dependent : changed.invalidates()) {
			if (!visited.add(dependent)) {
				continue;
			}
			ApplicationStep downstream = steps.get(dependent);
			if (downstream.getStatus() == StepStatus.COMPLETED || downstream.getStatus() == StepStatus.PENDING_EXTERNAL) {
				downstream.invalidate();
				record("STEP_INVALIDATED", dependent);
				invalidateDependents(dependent, visited);
			}
			else if (downstream.getStatus() == StepStatus.LOCKED
					&& application.getStatus() == ApplicationStatus.CORRECTION_REQUIRED) {
				// A corrected step changes what was checked and declared, so those are redone too
				downstream.reopen(DEPENDENCY_CHANGED, null);
				record("STEP_REOPENED", dependent);
				invalidateDependents(dependent, visited);
			}
		}
	}
}
