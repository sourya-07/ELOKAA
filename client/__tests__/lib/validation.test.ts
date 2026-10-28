import { checkFile, isPersonalPan, isPincode, mobileDigits, normaliseMobile, PAPER, PHOTO } from "@/lib/validation";

describe("normaliseMobile", () => {
	it.each([
		["9876543210", "9876543210"],
		["+91 98765 43210", "9876543210"],
		["098765-43210", "9876543210"],
		["5876543210", null],
		["12345", null],
		["98765432101", null],
	])("%s -> %s", (raw, expected) => {
		expect(normaliseMobile(raw)).toBe(expected);
	});
});

describe("mobileDigits", () => {
	it.each([
		["98765abc", "98765"],
		["11111111111111111111", "1111111111"],
		["+91 98765 43210", "9876543210"],
		["098765-43210", "9876543210"],
		["98765432101", "9876543210"],
	])("%s -> %s", (raw, expected) => {
		expect(mobileDigits(raw)).toBe(expected);
	});
});

describe("PAN and pincode", () => {
	it("accepts only personal PANs", () => {
		expect(isPersonalPan("abcpk1234l")).toBe(true);
		expect(isPersonalPan("ABCCK1234L")).toBe(false); // company
		expect(isPersonalPan("ABCPK123L")).toBe(false);
	});
	it("accepts 6-digit pincodes not starting with 0", () => {
		expect(isPincode("400050")).toBe(true);
		expect(isPincode("040050")).toBe(false);
	});
});

describe("checkFile", () => {
	const file = (type: string, size: number) => new File([new Uint8Array(size)], "f", { type });
	it("allows only JPG for photos and JPG or PDF for papers", () => {
		expect(checkFile(file("image/jpeg", 100), PHOTO)).toBeNull();
		expect(checkFile(file("image/png", 100), PHOTO)).toMatch(/Only JPG/);
		expect(checkFile(file("application/pdf", 100), PHOTO)).toMatch(/Only JPG/);
		expect(checkFile(file("application/pdf", 100), PAPER)).toBeNull();
	});
	it("enforces the size limit", () => {
		expect(checkFile(file("image/jpeg", 6 * 1024 * 1024), PHOTO)).toMatch(/5 MB/);
	});
});
