import { canOpen } from "@/features/apply/guards";
import { snapshotAt } from "../fixtures";

describe("canOpen", () => {
	it("opens the next step and steps already started, not future ones", () => {
		const snapshot = snapshotAt("CREDIT_CHECK");
		expect(canOpen(snapshot, "CREDIT_CHECK")).toBe(true);
		expect(canOpen(snapshot, "PAN")).toBe(true);
		expect(canOpen(snapshot, "PHOTOS")).toBe(false);
	});
	it("opens nothing once the application is submitted or closed", () => {
		expect(canOpen(snapshotAt("CREDIT_CHECK", { status: "INELIGIBLE" }), "CREDIT_CHECK")).toBe(false);
		expect(canOpen(snapshotAt(null, { status: "SUBMITTED" }), "PAN")).toBe(false);
	});
});
