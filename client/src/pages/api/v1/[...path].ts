import type { NextApiRequest, NextApiResponse } from "next";
import { randomUUID } from "node:crypto";
import {
	type Audience,
	COOKIE,
	FORWARDED_REQUEST_HEADERS,
	FORWARDED_RESPONSE_HEADERS,
	clearCookies,
	forwardedFor,
	isAllowedWrite,
	isSignOutRoute,
	isTokenRoute,
	refreshPath,
	serverPath,
	singleFlight,
	type TokenBody,
	tokenCookies,
	withoutTokens,
} from "@/lib/bff";

export const config = { api: { bodyParser: false, responseLimit: "12mb" } };

const SERVER_URL = process.env.ELOKA_SERVER_URL ?? "http://localhost:8091";
const MAX_BODY = 25 * 1024 * 1024;
const refreshOnce = singleFlight<TokenBody | null>();

/** The BFF proxy (ADR-0003): browser → this route → server, with tokens only in HttpOnly cookies. */
export default async function handler(req: NextApiRequest, res: NextApiResponse) {
	const method = (req.method ?? "GET").toUpperCase();
	const target = serverPath(req.query.path);
	if (!target) {
		return problem(res, 404, "NOT_FOUND", "Not found");
	}
	if (!isAllowedWrite(method, req.headers, req.headers.host ?? "")) {
		return problem(res, 403, "FORBIDDEN", "This request was refused.");
	}
	let body: Buffer | undefined;
	try {
		body = method === "GET" || method === "HEAD" ? undefined : await readBody(req);
	} catch {
		return problem(res, 413, "FILE_TOO_LARGE", "The upload is too large.");
	}
	const secure = process.env.NODE_ENV === "production";
	const requestId = typeof req.headers["x-request-id"] === "string" ? req.headers["x-request-id"] : randomUUID();
	const query = new URL(req.url ?? "", "http://local").search;
	const names = COOKIE[target.audience];
	const send = (accessToken: string | undefined) =>
		fetch(SERVER_URL + target.path + query, {
			method,
			headers: headersFor(req, accessToken, requestId),
			body: body ? new Uint8Array(body) : undefined,
			redirect: "manual",
		});

	let upstream: Response;
	const cookies: string[] = [];
	try {
		upstream = await send(req.cookies[names.access]);
		// An expired access token: rotate once with the refresh cookie, then retry the same call
		const refreshToken = req.cookies[names.refresh];
		if (upstream.status === 401 && !isTokenRoute(method, target.path) && refreshToken) {
			const rotated = await refresh(target.audience, refreshToken, requestId);
			if (rotated?.access_token) {
				cookies.push(...tokenCookies(target.audience, rotated, secure));
				upstream = await send(rotated.access_token);
			} else {
				cookies.push(...clearCookies(target.audience, secure));
			}
		}
	} catch {
		return problem(res, 503, "SERVER_UNAVAILABLE", "We could not reach ELOKA. Please try again in a moment.");
	}

	for (const name of FORWARDED_RESPONSE_HEADERS) {
		const value = upstream.headers.get(name);
		if (value) {
			res.setHeader(name, value);
		}
	}
	res.setHeader("x-request-id", upstream.headers.get("x-request-id") ?? requestId);
	if (!upstream.headers.get("cache-control")) {
		res.setHeader("cache-control", "no-store");
	}

	if (isTokenRoute(method, target.path) && upstream.ok) {
		const tokens = await upstream.json();
		res.setHeader("set-cookie", [...cookies, ...tokenCookies(target.audience, tokens, secure)]);
		return res.status(upstream.status).json(withoutTokens(tokens));
	}
	if (isSignOutRoute(method, target.path) || upstream.status === 401) {
		cookies.push(...clearCookies(target.audience, secure));
	}
	if (cookies.length) {
		res.setHeader("set-cookie", cookies);
	}
	res.status(upstream.status);
	const payload = Buffer.from(await upstream.arrayBuffer());
	return payload.length ? res.send(payload) : res.end();
}

function headersFor(request: NextApiRequest, accessToken: string | undefined, requestId: string) {
	const headers: Record<string, string> = { "x-request-id": requestId };
	for (const name of FORWARDED_REQUEST_HEADERS) {
		const value = request.headers[name];
		if (typeof value === "string") {
			headers[name] = value;
		}
	}
	headers["x-forwarded-for"] = forwardedFor(request.headers["x-forwarded-for"], request.socket.remoteAddress);
	if (accessToken) {
		headers.authorization = `Bearer ${accessToken}`;
	}
	return headers;
}

function refresh(audience: Audience, refreshToken: string, requestId: string): Promise<TokenBody | null> {
	return refreshOnce(`${audience}:${refreshToken}`, async () => {
		const response = await fetch(SERVER_URL + refreshPath(audience), {
			method: "POST",
			headers: { "content-type": "application/json", "x-request-id": requestId },
			body: JSON.stringify({ refresh_token: refreshToken }),
		});
		return response.ok ? ((await response.json()) as TokenBody) : null;
	});
}

function readBody(req: NextApiRequest): Promise<Buffer> {
	return new Promise((resolve, reject) => {
		const chunks: Buffer[] = [];
		let size = 0;
		req.on("data", (chunk: Buffer) => {
			size += chunk.length;
			if (size > MAX_BODY) {
				reject(new Error("too large"));
				req.destroy();
				return;
			}
			chunks.push(chunk);
		});
		req.on("end", () => resolve(Buffer.concat(chunks)));
		req.on("error", reject);
	});
}

function problem(res: NextApiResponse, status: number, code: string, detail: string) {
	res.setHeader("cache-control", "no-store");
	return res.status(status).json({ status, code, detail });
}
