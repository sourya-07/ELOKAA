import { milestonesOf, progressOf } from "@/lib/milestones";
import { snapshotAt } from "../fixtures";

describe("milestones", () => {
	it("counts finished parts from the server's step statuses", () => {
		expect(progressOf(snapshotAt("PAN"))).toEqual({ done: 1, total: 6 }); // identity still needs the PAN
		expect(progressOf(snapshotAt("CREDIT_CHECK"))).toEqual({ done: 2, total: 6 });
		expect(progressOf(snapshotAt("PHOTOS"))).toEqual({ done: 3, total: 6 });
		const states = milestonesOf(snapshotAt("CREDIT_CHECK"));
		expect(states.find((s) => s.key === "ELIGIBILITY")?.current).toBe(true);
	});

	it("counts review only once the application is submitted", () => {
		expect(progressOf(snapshotAt(null)).done).toBe(5);
		expect(progressOf(snapshotAt(null, { status: "SUBMITTED" })).done).toBe(6);
	});

	it("ignores steps a journey version does not have", () => {
		const full = snapshotAt("PAN");
		const snapshot = { ...full, steps: full.steps.filter((s) => s.step_key !== "EMAIL_VERIFICATION") }; // an older application
		expect(milestonesOf(snapshot).find((s) => s.key === "CONTACT")?.done).toBe(true);
	});
});
