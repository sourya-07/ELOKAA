import { pagesFor, routeFor } from "@/lib/steps";
import { snapshotAt } from "../fixtures";

describe("routeFor", () => {
	it("follows the server's next step on each path", () => {
		expect(routeFor(snapshotAt("EMAIL_VERIFICATION"))).toBe("/apply");
		expect(routeFor(snapshotAt("CONSENT"))).toBe("/apply");
		expect(routeFor(snapshotAt("VERIFICATION_METHOD"))).toBe("/apply/method");
		expect(routeFor(snapshotAt("AADHAAR"))).toBe("/apply/aadhaar");
		expect(routeFor(snapshotAt("AADHAAR", { method: "DIGILOCKER" }))).toBe("/apply/digilocker");
		expect(routeFor(snapshotAt("PAN", { method: "DIGILOCKER" }))).toBe("/apply/digilocker");
		expect(routeFor(snapshotAt("PAN"))).toBe("/apply/pan");
		expect(routeFor(snapshotAt("CREDIT_CHECK"))).toBe("/apply/credit-check");
		// DigiLocker gave no full address: the address page is still reachable
		expect(routeFor(snapshotAt("HOME_ADDRESS", { method: "DIGILOCKER" }))).toBe("/apply/address");
		expect(routeFor(snapshotAt("ID_DOCUMENT"))).toBe("/apply/id-document");
		expect(routeFor(snapshotAt("DECLARATION"))).toBe("/apply/review");
	});
	it("shows the status page once nothing can be changed", () => {
		expect(routeFor(snapshotAt(null, { status: "SUBMITTED" }))).toBe("/apply/status");
		expect(routeFor(snapshotAt("PHOTOS", { status: "INELIGIBLE" }))).toBe("/apply/status");
	});
});

describe("steps of each path", () => {
	it("has 10 pages step by step and 8 with DigiLocker", () => {
		expect(pagesFor("MANUAL")).toHaveLength(10);
		expect(pagesFor("DIGILOCKER")).toHaveLength(8);
		expect(pagesFor("DIGILOCKER").map((p) => p.path)).not.toContain("/apply/address");
	});
});
