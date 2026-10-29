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

/**
 * Results that came from a provider check (DigiLocker, the PAN and ID records, the credit bureau). They
 * are shown read-only: no "Change". If our team needs one redone, they reopen the step and its form
 * opens by itself (status REOPENED).
 */
const VERIFIED: StepKey[] = ["AADHAAR", "PAN", "CREDIT_CHECK", "ALTERNATE_ID"];

/**
 * A done step the applicant may still change (not frozen during a review): what they supplied
 * themselves (email, a typed address, photos, documents), never a verified result, and never an
 * address that came from DigiLocker.
 */
export function canChange(snapshot: Snapshot, key: StepKey): boolean {
	const step = snapshot.steps.find((s) => s.step_key === key);
	if (!step || step.status !== "COMPLETED" || !["IN_PROGRESS", "CORRECTION_REQUIRED"].includes(snapshot.status)) {
		return false;
	}
	if (VERIFIED.includes(key)) {
		return false;
	}
	return !(key === "HOME_ADDRESS" && step.summary?.source === "DIGILOCKER");
}
