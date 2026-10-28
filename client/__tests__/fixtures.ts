import type { Snapshot, StepKey, StepStatus } from "@/lib/types";

const KEYS: StepKey[] = ["MOBILE_VERIFICATION", "EMAIL_VERIFICATION", "CONSENT", "AADHAAR", "HOME_ADDRESS", "PAN", "CREDIT_CHECK", "ALTERNATE_ID", "PHOTOS", "KYC_DOCUMENTS", "EDUCATION", "DECLARATION"];

/** A snapshot where every step before {@code next} is done. */
export function snapshotAt(next: StepKey | null, overrides: Partial<Snapshot> = {}): Snapshot {
	const nextIndex = next ? KEYS.indexOf(next) : KEYS.length;
	return {
		application_id: "ptn_TEST",
		status: "IN_PROGRESS",
		version: 3,
		method: "DIGILOCKER",
		next_step: next,
		expires_at: "2026-11-26T00:00:00Z",
		submitted_at: null,
		available_actions: [],
		steps: KEYS.map((key, index) => ({
			step_key: key,
			status: (index < nextIndex ? "COMPLETED" : "NOT_STARTED") as StepStatus,
			attempt_count: 0,
			max_attempts: 3,
		})),
		...overrides,
	};
}
