/**
 * Calls to the server through this app's own proxy (/api/v1/**, ADR-0003). Cookies carry the
 * session; nothing personal is logged or stored in the browser.
 */

import { errorMessage } from "@/lib/errors";

export interface Problem {
	status: number;
	code: string;
	detail?: string;
	invalid_params?: Record<string, string>;
	attempts_left?: number;
	retry_after?: number;
	missing_steps?: string[];
	missing_documents?: string[];
	current?: unknown;
	[key: string]: unknown;
}

export class ApiError extends Error {
	readonly status: number;
	readonly problem: Problem;

	constructor(problem: Problem) {
		super(problem.detail ?? problem.code);
		this.status = problem.status;
		this.problem = problem;
	}

	get code(): string {
		return this.problem.code;
	}
}

export interface CallOptions {
	method?: "GET" | "POST" | "PUT" | "DELETE";
	body?: unknown;
	ifMatch?: string;
	/** true for a new key, or a key to reuse when the same action is retried */
	idempotent?: boolean | string;
	form?: FormData;
}

export interface Reply<T> {
	data: T;
	status: number;
	etag: string | null;
}

/** One call. Throws {@link ApiError} for any non-2xx answer, with the server's problem+json. */
export async function call<T>(path: string, options: CallOptions = {}): Promise<Reply<T>> {
	const method = options.method ?? (options.body !== undefined || options.form ? "POST" : "GET");
	const headers: Record<string, string> = { "x-requested-with": "eloka", accept: "application/json" };
	if (options.ifMatch) {
		headers["if-match"] = options.ifMatch;
	}
	if (options.idempotent) {
		headers["idempotency-key"] = typeof options.idempotent === "string" ? options.idempotent : newKey();
	}
	let body: BodyInit | undefined;
	if (options.form) {
		body = options.form;
	} else if (options.body !== undefined) {
		headers["content-type"] = "application/json";
		body = JSON.stringify(options.body);
	}
	let response: Response;
	try {
		response = await fetch(`/api${path}`, { method, headers, body, credentials: "same-origin" });
	} catch {
		throw new ApiError({ status: 0, code: "NETWORK", detail: "No connection. Check your internet and try again." });
	}
	const text = await response.text();
	const data = text ? safeJson(text) : undefined;
	if (!response.ok) {
		const problem = (data && typeof data === "object" ? data : {}) as Partial<Problem>;
		throw new ApiError({
			...problem,
			status: response.status,
			code: problem.code ?? `HTTP_${response.status}`,
			detail: problem.detail ?? "Something went wrong. Please try again.",
		});
	}
	return { data: data as T, status: response.status, etag: response.headers.get("etag") };
}

/**
 * A file upload that reports how much has been sent (fetch cannot). Same rules as {@link call}:
 * through the proxy, `X-Requested-With`, and an {@link ApiError} with the server's problem.
 * Progress covers the browser-to-proxy leg; checking the file on the server comes after 100%.
 */
export function uploadWithProgress<T>(path: string, form: FormData, onProgress?: (share: number) => void, signal?: AbortSignal): Promise<Reply<T>> {
	return new Promise((resolve, reject) => {
		const request = new XMLHttpRequest();
		// Cancelling stops the sending; a file the server already received in full is still checked and kept
		signal?.addEventListener("abort", () => request.abort());
		request.onabort = () => reject(new ApiError({ status: 0, code: "ABORTED", detail: "Upload cancelled." }));
		request.open("POST", `/api${path}`);
		request.setRequestHeader("x-requested-with", "eloka");
		request.setRequestHeader("accept", "application/json");
		request.upload.onprogress = (event) => {
			if (event.lengthComputable) {
				onProgress?.(event.loaded / event.total);
			}
		};
		request.onerror = () =>
			reject(new ApiError({ status: 0, code: "NETWORK", detail: "No connection. Check your internet and try again." }));
		request.onload = () => {
			const data = request.responseText ? safeJson(request.responseText) : undefined;
			if (request.status < 200 || request.status >= 300) {
				const problem = (data && typeof data === "object" ? data : {}) as Partial<Problem>;
				reject(
					new ApiError({
						...problem,
						status: request.status,
						code: problem.code ?? `HTTP_${request.status}`,
						detail: problem.detail ?? "Something went wrong. Please try again.",
					}),
				);
				return;
			}
			resolve({ data: data as T, status: request.status, etag: request.getResponseHeader("etag") });
		};
		request.send(form);
	});
}

function safeJson(text: string): unknown {
	try {
		return JSON.parse(text);
	} catch {
		return undefined;
	}
}

export function newKey(): string {
	if (typeof crypto !== "undefined" && "randomUUID" in crypto) {
		return crypto.randomUUID();
	}
	return `${Date.now()}-${Math.random().toString(16).slice(2)}`;
}

/**
 * A sentence for the person, from a problem: a field's own message first, then the catalogue's words
 * for the code (lib/errors.ts, docs/ERROR_CATALOG.md), then the server's detail.
 */
export function messageOf(error: unknown): string {
	if (error instanceof ApiError) {
		const fields = error.problem.invalid_params;
		if (fields && Object.keys(fields).length) {
			return Object.values(fields)[0];
		}
		return errorMessage(error.problem) ?? error.problem.detail ?? "Something went wrong. Please try again.";
	}
	return "Something went wrong. Please try again.";
}
