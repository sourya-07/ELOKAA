import { fakeJpeg, jpegFile, pdfFile, pngFile } from "../files";
import { checkFile, isPersonalPan, isPincode, mobileDigits, normaliseEmail, normaliseMobile, PAPER, PHOTO, ruleFrom } from "@/lib/validation";

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
	const KB = 1024;
	const MB = 1024 * KB;

	it("accepts JPG, PNG and PDF documents of the right size, read from their bytes", async () => {
		expect(await checkFile(jpegFile(50 * KB), PAPER)).toBeNull();
		expect(await checkFile(pngFile(50 * KB), PAPER)).toBeNull();
		expect(await checkFile(pdfFile(50 * KB), PAPER)).toBeNull();
		expect(await checkFile(jpegFile(50 * KB), PHOTO)).toBeNull();
		expect(await checkFile(pngFile(50 * KB), PHOTO)).toBeNull();
	});

	it("refuses other formats, whatever the name or browser type says", async () => {
		expect(await checkFile(fakeJpeg(50 * KB), PAPER)).toBe("Only JPG, PNG or PDF files are accepted.");
		expect(await checkFile(pdfFile(50 * KB), PHOTO)).toBe("Only JPG or PNG files are accepted.");
		expect(await checkFile(new File([], "empty.pdf", { type: "application/pdf" }), PAPER)).toMatch(/empty/);
	});

	it("keeps documents between 10 KB and 5 MB, and photos between 20 KB and 2 MB", async () => {
		expect(await checkFile(pdfFile(9 * KB), PAPER)).toMatch(/smaller than 10 KB/);
		expect(await checkFile(pdfFile(5 * MB + 1), PAPER)).toMatch(/larger than 5 MB/);
		expect(await checkFile(jpegFile(19 * KB), PHOTO)).toMatch(/smaller than 20 KB/);
		expect(await checkFile(jpegFile(2 * MB + 1), PHOTO)).toMatch(/larger than 2 MB/);
		expect(await checkFile(jpegFile(2 * MB), PHOTO)).toBeNull();
	});

	it("refuses a cut-off or damaged file", async () => {
		expect(await checkFile(jpegFile(50 * KB, { cut: true }), PAPER)).toMatch(/damaged or incomplete/);
		expect(await checkFile(pngFile(50 * KB, { cut: true }), PAPER)).toMatch(/damaged or incomplete/);
		expect(await checkFile(pdfFile(50 * KB, { cut: true }), PAPER)).toMatch(/damaged or incomplete/);
	});
});

// The same cases as the server's EmailRuleTest, so the two checks stay one rule
describe("normaliseEmail", () => {
	it.each([
		["name@example.com", "name@example.com"],
		["  Name.Surname+tag@Example.CO.IN ", "name.surname+tag@example.co.in"],
		["o'brien@example.org", "o'brien@example.org"],
		["user@sub.domain.example", "user@sub.domain.example"],
		["name.example.com", null],
		["name@example", null],
		["name@example.c", null],
		["name @example.com", null],
		["@example.com", null],
		["a@b@example.com", null],
		[`${"a".repeat(65)}@example.com`, null],
		[`a@${"b".repeat(250)}.com`, null],
	])("%s -> %s", (raw, expected) => {
		expect(normaliseEmail(raw)).toBe(expected);
	});
});

// The built-in copies used before the server's rules load must say what the server says
// (DocumentKind: photos JPEG ≤ 5 MB, ≥ 300 px; papers JPEG or PDF ≤ 10 MB)
describe("ruleFrom", () => {
	it("words the server's rules, and the built-in copies match them", () => {
		// as GET /v1/partner/document-rules answers (formats sorted)
		const photo = ruleFrom({ kind: "PASSPORT_PHOTO", formats: ["image/jpeg", "image/png"], min_bytes: 20 * 1024, max_bytes: 2 * 1024 * 1024, min_side_px: 300, max_count: 1 });
		const paper = ruleFrom({ kind: "PAN_CARD", formats: ["application/pdf", "image/jpeg", "image/png"], min_bytes: 10 * 1024, max_bytes: 5 * 1024 * 1024, min_side_px: 0, max_count: 1 });
		expect(photo).toEqual(PHOTO);
		expect(paper).toEqual(PAPER);
		expect(paper.label).toBe("JPG, PNG or PDF, 10 KB to 5 MB");
	});
});
