import type { Snapshot, StepKey } from "@/lib/types";

/**
 * A page may be shown for its step when the server says it is next, or when it was started before
 * (to change it or see why it failed), as long as the application can still be changed.
 */
export function canOpen(snapshot: Snapshot, step: StepKey): boolean {
	if (!["IN_PROGRESS", "CORRECTION_REQUIRED"].includes(snapshot.status)) {
		return false;
	}
	const current = snapshot.steps.find((s) => s.step_key === step);
	return snapshot.next_step === step || (!!current && current.status !== "NOT_STARTED" && current.status !== "LOCKED");
}
