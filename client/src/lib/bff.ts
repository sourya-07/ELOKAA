/**
 * Server-side proxy logic (ADR-0003). The browser never sees session tokens: they live in
 * HttpOnly cookies that only this proxy reads, and it adds them as a Bearer header when it
 * forwards a call to the server over the internal network. Kept free of Next.js types so it can
 * be unit-tested.
 */

export type Audience = "partner" | "admin";

export const COOKIE = {
	partner: { access: "ptn_at", refresh: "ptn_rt" },
	admin: { access: "stf_at", refresh: "stf_rt" },
} as const;

/** Calls that hand out tokens; the proxy keeps the tokens and gives the browser the rest. */
const TOKEN_ROUTES = new Set([
	"POST /v1/partner/otp/verify",
	"POST /v1/partner/session/refresh",
	"POST /v1/admin/auth/otp/verify",
	"POST /v1/admin/auth/google",
	"POST /v1/admin/auth/refresh",
]);

const SIGN_OUT_ROUTES = new Set(["DELETE /v1/partner/session", "DELETE /v1/admin/auth/session"]);

/** Request headers passed to the server. Everything else (cookies included) stays here. */
export const FORWARDED_REQUEST_HEADERS = ["content-type", "if-match", "idempotency-key", "accept"];

/** Response headers passed back to the browser. */
export const FORWARDED_RESPONSE_HEADERS = [
	"content-type",
	"etag",
	"cache-control",
	"content-disposition",
	"retry-after",
	"x-request-id",
	"idempotent-replayed",
	"x-content-type-options",
];

const SAFE_SEGMENT = /^[A-Za-z0-9_-]+$/;

/**
 * Turns the catch-all route parts into a server path, or null when the path is not one the proxy
 * may reach. Only {@code /v1/partner/**} and {@code /v1/admin/**}, plain segments only, so no
 * request can be steered anywhere else on the server.
 */
export function serverPath(parts: string[] | string | undefined): { path: string; audience: Audience } | null {
	const segments = Array.isArray(parts) ? parts : parts ? [parts] : [];
	if (segments.length < 2 || !segments.every((s) => SAFE_SEGMENT.test(s))) {
		return null;
	}
	const [area] = segments;
	if (area !== "partner" && area !== "admin") {
		return null;
	}
	return { path: "/v1/" + segments.join("/"), audience: area };
}

export function isTokenRoute(method: string, path: string): boolean {
	return TOKEN_ROUTES.has(`${method} ${path}`);
}

export function isSignOutRoute(method: string, path: string): boolean {
	return SIGN_OUT_ROUTES.has(`${method} ${path}`);
}

export function refreshPath(audience: Audience): string {
	return audience === "partner" ? "/v1/partner/session/refresh" : "/v1/admin/auth/refresh";
}

/**
 * Cross-site request protection for state-changing calls: the custom header cannot be sent
 * cross-site without a CORS preflight (which this app never allows), and the Origin, when
 * present, must be this site. Together with SameSite=Strict cookies.
 */
export function isAllowedWrite(method: string, headers: Record<string, string | string[] | undefined>, host: string): boolean {
	if (method === "GET" || method === "HEAD") {
		return true;
	}
	if (headers["x-requested-with"] !== "eloka") {
		return false;
	}
	const origin = headers.origin;
	if (typeof origin === "string") {
		try {
			return new URL(origin).host === host;
		} catch {
			return false;
		}
	}
	return true;
}

export interface TokenBody {
	access_token?: string;
	refresh_token?: string;
	expires_in?: number;
	[key: string]: unknown;
}

/** The token response without the tokens: what the browser is allowed to see. */
const TOKEN_FIELDS = new Set(["access_token", "refresh_token", "token_type"]);

export function withoutTokens(body: TokenBody): Record<string, unknown> {
	const rest = Object.fromEntries(Object.entries(body).filter(([key]) => !TOKEN_FIELDS.has(key)));
	return { signed_in: true, ...rest };
}

/** Set-Cookie values for a token pair. The refresh cookie lives as long as the server's absolute session limit. */
export function tokenCookies(audience: Audience, body: TokenBody, secure: boolean): string[] {
	const names = COOKIE[audience];
	const flags = `Path=/api; HttpOnly; SameSite=Strict${secure ? "; Secure" : ""}`;
	const accessAge = Math.max(60, Math.floor(body.expires_in ?? 1800));
	return [
		`${names.access}=${body.access_token}; Max-Age=${accessAge}; ${flags}`,
		`${names.refresh}=${body.refresh_token}; Max-Age=${12 * 3600}; ${flags}`,
	];
}

export function clearCookies(audience: Audience, secure: boolean): string[] {
	const names = COOKIE[audience];
	const flags = `Path=/api; HttpOnly; SameSite=Strict${secure ? "; Secure" : ""}`;
	return [`${names.access}=; Max-Age=0; ${flags}`, `${names.refresh}=; Max-Age=0; ${flags}`];
}

/**
 * Runs one refresh per refresh token, shared by every request that needs it. Two calls that find
 * the access token expired at the same moment must not both rotate: the server treats a second use
 * of the old refresh token as theft and ends the session. The result is kept for a few seconds for
 * calls that arrive just after. This works within one proxy process (one client instance).
 */
export function singleFlight<T>(windowMs = 10_000) {
	const inFlight = new Map<string, { promise: Promise<T>; at: number }>();
	return (key: string, run: () => Promise<T>, now = Date.now()): Promise<T> => {
		for (const [k, entry] of inFlight) {
			if (now - entry.at > windowMs) {
				inFlight.delete(k);
			}
		}
		const existing = inFlight.get(key);
		if (existing) {
			return existing.promise;
		}
		const promise = run();
		inFlight.set(key, { promise, at: now });
		return promise;
	};
}

/** Appends the browser's address to the chain the edge proxy started. */
export function forwardedFor(existing: string | string[] | undefined, remote: string | undefined): string {
	const prior = Array.isArray(existing) ? existing.join(", ") : existing;
	return [prior, remote].filter(Boolean).join(", ");
}
