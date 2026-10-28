import type { TaskState } from "@/components/TaskSection";
import type { Snapshot, StepKey } from "@/lib/types";

/**
 * How a step shows as a task, from the server's status only: done (or frozen) folds away, a
 * failure or a reviewer's request needs attention, the server's next step (or one under way) is
 * current, and anything whose earlier steps are not done waits.
 */
export function taskState(snapshot: Snapshot, key: StepKey): TaskState {
	const step = snapshot.steps.find((s) => s.step_key === key);
	if (!step) {
		return "locked";
	}
	switch (step.status) {
		case "COMPLETED":
		case "LOCKED":
		case "NOT_APPLICABLE":
			return "done";
		case "BLOCKED":
			return "blocked";
		case "FAILED":
		case "REOPENED":
			return "attention";
		case "PENDING_EXTERNAL":
		case "IN_PROGRESS":
			return "current";
		default:
			return snapshot.next_step === key ? "current" : "locked";
	}
}

/** A done step the applicant may still change (not frozen during a review). */
export function canChange(snapshot: Snapshot, key: StepKey): boolean {
	const step = snapshot.steps.find((s) => s.step_key === key);
	return !!step && step.status === "COMPLETED" && ["IN_PROGRESS", "CORRECTION_REQUIRED"].includes(snapshot.status);
}
