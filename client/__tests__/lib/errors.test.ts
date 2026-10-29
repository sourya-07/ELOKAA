import { readFileSync } from "fs";
import { join } from "path";
import { ApiError, messageOf } from "@/lib/api";
import { ERRORS } from "@/lib/errors";
import { ERROR_TEXT } from "@/lib/steps";

/** The backticked codes in the first column of the table under a heading of docs/ERROR_CATALOG.md. */
function catalogued(heading: string): string[] {
	const text = readFileSync(join(__dirname, "../../../docs/ERROR_CATALOG.md"), "utf8");
	const start = text.indexOf(heading);
	expect(start).toBeGreaterThanOrEqual(0);
	const end = text.indexOf("\n## ", start + heading.length);
	const body = text.slice(start, end < 0 ? undefined : end);
	return [...body.matchAll(/^\| `([A-Z0-9_]+)` \|/gm)].map((m) => m[1]);
}

describe("error catalog", () => {
	it("has the client's words for every error the server can send", () => {
		const http = catalogued("## HTTP errors");
		expect(http.length).toBeGreaterThan(50);
		expect(http.filter((code) => !(code in ERRORS))).toEqual([]);
	});

	it("has the client's words for every step result", () => {
		const steps = catalogued("## Step results");
		expect(steps.length).toBeGreaterThan(20);
		expect(steps.filter((code) => !(code in ERROR_TEXT))).toEqual([]);
	});
});

describe("messageOf", () => {
	it("prefers a field's own message, then the catalogue, then the server's detail", () => {
		expect(messageOf(new ApiError({ status: 422, code: "VALIDATION_FAILED", detail: "x", invalid_params: { email: "Enter a valid email address" } }))).toBe(
			"Enter a valid email address",
		);
		expect(messageOf(new ApiError({ status: 409, code: "STEP_NOT_EDITABLE", detail: "This step cannot be changed now" }))).toBe("This part cannot be changed now.");
		expect(messageOf(new ApiError({ status: 422, code: "OTP_INVALID", attempts_left: 2 }))).toBe("The code is not correct. 2 attempts left.");
		expect(messageOf(new ApiError({ status: 429, code: "OTP_LIMIT_REACHED", retry_after: 60 }))).toBe("Too many codes asked for. You can ask again in 1 min.");
		expect(messageOf(new ApiError({ status: 409, code: "DOCUMENTS_MISSING", missing_documents: ["PAN_CARD"] }))).toBe("Add your PAN card.");
		expect(messageOf(new ApiError({ status: 418, code: "SOMETHING_NEW", detail: "The server's words" }))).toBe("The server's words");
		expect(messageOf(new Error("boom"))).toBe("Something went wrong. Please try again.");
	});
});
