import { milestonesOf, progressOf } from "@/lib/milestones";
import { snapshotAt } from "../fixtures";

describe("milestones", () => {
	it("counts finished parts from the server's step statuses", () => {
		expect(progressOf(snapshotAt("CREDIT_CHECK"))).toEqual({ done: 1, total: 6 }); // identity still has the address
		expect(progressOf(snapshotAt("PHOTOS"))).toEqual({ done: 3, total: 6 });
		const states = milestonesOf(snapshotAt("CREDIT_CHECK"));
		expect(states.find((s) => s.key === "ELIGIBILITY")?.current).toBe(true);
	});

	it("counts review only once the application is submitted", () => {
		expect(progressOf(snapshotAt(null)).done).toBe(5);
		expect(progressOf(snapshotAt(null, { status: "SUBMITTED" })).done).toBe(6);
	});

	it("ignores steps a journey version does not have", () => {
		const snapshot = snapshotAt("PAN");
		expect(snapshot.steps.some((s) => s.step_key === "EMAIL_VERIFICATION")).toBe(false);
		expect(milestonesOf(snapshot).find((s) => s.key === "CONTACT")?.done).toBe(true);
	});
});
