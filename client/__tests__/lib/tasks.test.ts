import { canChange, taskState } from "@/lib/tasks";
import type { StepKey } from "@/lib/types";
import { snapshotAt } from "../fixtures";

function withSummary(key: StepKey, summary: Record<string, unknown>) {
	const snapshot = snapshotAt(null);
	snapshot.steps = snapshot.steps.map((s) => (s.step_key === key ? { ...s, summary } : s));
	return snapshot;
}

describe("canChange", () => {
	it("keeps verified results read-only", () => {
		const done = snapshotAt(null);
		for (const key of ["AADHAAR", "PAN", "CREDIT_CHECK", "ALTERNATE_ID"] as StepKey[]) {
			expect(canChange(done, key)).toBe(false);
		}
	});

	it("lets the applicant change what they supplied themselves", () => {
		const done = snapshotAt(null);
		for (const key of ["EMAIL_VERIFICATION", "PHOTOS", "KYC_DOCUMENTS", "EDUCATION"] as StepKey[]) {
			expect(canChange(done, key)).toBe(true);
		}
		expect(canChange(withSummary("HOME_ADDRESS", { source: "SELF_DECLARED" }), "HOME_ADDRESS")).toBe(true);
		expect(canChange(withSummary("HOME_ADDRESS", { source: "DIGILOCKER" }), "HOME_ADDRESS")).toBe(false);
	});

	it("changes nothing once submitted", () => {
		expect(canChange(snapshotAt(null, { status: "SUBMITTED" }), "EMAIL_VERIFICATION")).toBe(false);
	});

	it("opens a verified step our team reopened, without a Change button", () => {
		const snapshot = snapshotAt(null, { status: "CORRECTION_REQUIRED" });
		snapshot.steps = snapshot.steps.map((s) => (s.step_key === "PAN" ? { ...s, status: "REOPENED" } : s));
		expect(taskState(snapshot, "PAN")).toBe("attention");
		expect(canChange(snapshot, "PAN")).toBe(false);
	});
});
