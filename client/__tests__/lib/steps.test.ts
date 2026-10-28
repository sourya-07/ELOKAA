import { PAGES, routeFor, safeNext } from "@/lib/steps";
import { snapshotAt } from "../fixtures";

describe("routeFor", () => {
	it("follows the server's next step to its page", () => {
		expect(routeFor(snapshotAt("EMAIL_VERIFICATION"))).toBe("/apply");
		expect(routeFor(snapshotAt("CONSENT"))).toBe("/apply");
		for (const step of ["AADHAAR", "HOME_ADDRESS", "PAN", "CREDIT_CHECK", "ALTERNATE_ID"] as const) {
			expect(routeFor(snapshotAt(step))).toBe("/apply/identity");
		}
		expect(routeFor(snapshotAt("PHOTOS"))).toBe("/apply/photos");
		expect(routeFor(snapshotAt("KYC_DOCUMENTS"))).toBe("/apply/documents");
		expect(routeFor(snapshotAt("EDUCATION"))).toBe("/apply/documents");
		expect(routeFor(snapshotAt("DECLARATION"))).toBe("/apply/review");
	});
	it("shows the status page once nothing can be changed", () => {
		expect(routeFor(snapshotAt(null, { status: "SUBMITTED" }))).toBe("/apply/status");
		expect(routeFor(snapshotAt("PHOTOS", { status: "INELIGIBLE" }))).toBe("/apply/status");
	});
});

describe("pages", () => {
	it("puts the five identity steps on one page", () => {
		expect(PAGES.filter((page) => page.path.startsWith("/apply/identity"))).toHaveLength(5);
	});
	it("only follows return addresses inside the journey", () => {
		expect(safeNext("/apply/identity?state=dls_x")).toBe("/apply/identity?state=dls_x");
		expect(safeNext("https://evil.example")).toBeNull();
		expect(safeNext("//evil.example")).toBeNull();
		expect(safeNext(undefined)).toBeNull();
	});
});
