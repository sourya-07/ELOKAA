import { ApiError, call, messageOf } from "@/lib/api";

function mockFetch(status: number, body: unknown, headers: Record<string, string> = {}) {
	const fetchMock = jest.fn().mockResolvedValue({
		ok: status >= 200 && status < 300,
		status,
		headers: { get: (name: string) => headers[name.toLowerCase()] ?? null },
		text: async () => (body === undefined ? "" : JSON.stringify(body)),
	});
	global.fetch = fetchMock as unknown as typeof fetch;
	return fetchMock;
}

describe("call", () => {
	it("goes through the proxy with the CSRF header, If-Match and an Idempotency-Key", async () => {
		const fetchMock = mockFetch(200, { ok: true }, { etag: '"v4"' });
		const reply = await call("/v1/partner/applications/ptn_1/submit", { method: "POST", ifMatch: '"v3"', idempotent: true });
		const [url, init] = fetchMock.mock.calls[0];
		expect(url).toBe("/api/v1/partner/applications/ptn_1/submit");
		expect(init.headers["x-requested-with"]).toBe("eloka");
		expect(init.headers["if-match"]).toBe('"v3"');
		expect(init.headers["idempotency-key"]).toMatch(/.+/);
		expect(reply.etag).toBe('"v4"');
	});

	it("turns problem+json into an ApiError", async () => {
		mockFetch(422, { code: "OTP_INVALID", detail: "The code is not correct.", attempts_left: 2 });
		await expect(call("/v1/partner/otp/verify", { body: {} })).rejects.toMatchObject({ status: 422, code: "OTP_INVALID" });
	});

	it("reports a lost connection plainly", async () => {
		global.fetch = jest.fn().mockRejectedValue(new TypeError("Failed to fetch")) as unknown as typeof fetch;
		await expect(call("/v1/partner/otp", { body: {} })).rejects.toMatchObject({ code: "NETWORK" });
	});
});

describe("messageOf", () => {
	it("prefers the field message", () => {
		const error = new ApiError({ status: 422, code: "VALIDATION_FAILED", detail: "x", invalid_params: { pan: "Enter a valid PAN" } });
		expect(messageOf(error)).toBe("Enter a valid PAN");
	});
	it("never shows internals for unknown errors", () => {
		expect(messageOf(new Error("NullPointerException at …"))).toBe("Something went wrong. Please try again.");
	});
});
