import {
	clearCookies,
	forwardedFor,
	isAllowedWrite,
	isTokenRoute,
	serverPath,
	singleFlight,
	tokenCookies,
	withoutTokens,
} from "@/lib/bff";

describe("serverPath", () => {
	it("allows only partner and admin routes with plain segments", () => {
		expect(serverPath(["partner", "applications", "ptn_01ABC"])).toEqual({
			path: "/v1/partner/applications/ptn_01ABC",
			audience: "partner",
		});
		expect(serverPath(["admin", "me"])?.audience).toBe("admin");
		expect(serverPath(["system", "info"])).toBeNull();
		expect(serverPath(["partner", ".."])).toBeNull();
		expect(serverPath(["partner", "a%2F..%2F"])).toBeNull();
		expect(serverPath(["partner"])).toBeNull();
		expect(serverPath(undefined)).toBeNull();
	});
});

describe("isAllowedWrite", () => {
	it("lets reads through", () => {
		expect(isAllowedWrite("GET", {}, "apply.example")).toBe(true);
	});
	it("needs the custom header and a same-site origin for writes", () => {
		expect(isAllowedWrite("POST", {}, "apply.example")).toBe(false);
		expect(isAllowedWrite("POST", { "x-requested-with": "eloka" }, "apply.example")).toBe(true);
		expect(
			isAllowedWrite("POST", { "x-requested-with": "eloka", origin: "https://evil.example" }, "apply.example"),
		).toBe(false);
		expect(
			isAllowedWrite("POST", { "x-requested-with": "eloka", origin: "https://apply.example" }, "apply.example"),
		).toBe(true);
	});
});

describe("tokens", () => {
	const body = { access_token: "ptn_at_x", refresh_token: "ptn_rt_y", token_type: "Bearer", expires_in: 1800 };

	it("never returns tokens to the browser", () => {
		expect(withoutTokens(body)).toEqual({ signed_in: true, expires_in: 1800 });
	});

	it("keeps them in HttpOnly, SameSite=Strict cookies scoped to the proxy", () => {
		const [access, refresh] = tokenCookies("partner", body, true);
		expect(access).toContain("ptn_at=ptn_at_x");
		expect(access).toContain("HttpOnly");
		expect(access).toContain("SameSite=Strict");
		expect(access).toContain("Secure");
		expect(access).toContain("Path=/api");
		expect(refresh).toContain("ptn_rt=ptn_rt_y");
	});

	it("clears both cookies on sign-out", () => {
		expect(clearCookies("admin", false).every((c) => c.includes("Max-Age=0"))).toBe(true);
	});

	it("knows which routes hand out tokens", () => {
		expect(isTokenRoute("POST", "/v1/partner/otp/verify")).toBe(true);
		expect(isTokenRoute("POST", "/v1/partner/otp")).toBe(false);
	});
});

describe("singleFlight", () => {
	it("shares one refresh between calls that arrive together, and starts a new one later", async () => {
		const once = singleFlight<number>(1000);
		let runs = 0;
		const run = () => Promise.resolve(++runs);
		const [a, b] = await Promise.all([once("rt1", run, 0), once("rt1", run, 5)]);
		expect([a, b]).toEqual([1, 1]);
		expect(await once("rt1", run, 2000)).toBe(2);
		expect(await once("rt2", run, 2000)).toBe(3);
	});
});

describe("forwardedFor", () => {
	it("appends the browser address to what the edge sent", () => {
		expect(forwardedFor("203.0.113.9", "172.18.0.5")).toBe("203.0.113.9, 172.18.0.5");
		expect(forwardedFor(undefined, "127.0.0.1")).toBe("127.0.0.1");
	});
});
