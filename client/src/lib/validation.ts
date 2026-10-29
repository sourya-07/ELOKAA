/** Checks done in the browser for quick feedback. The server checks everything again. */

/** The digits of a number, without a leading +91 or 0 on a full-length number. */
function localDigits(raw: string): string {
	const digits = raw.replace(/\D/g, "");
	if (digits.length === 12 && digits.startsWith("91")) {
		return digits.slice(2);
	}
	if (digits.length === 11 && digits.startsWith("0")) {
		return digits.slice(1);
	}
	return digits;
}

/** What the mobile field keeps as the person types or pastes: digits only, never more than 10. */
export function mobileDigits(raw: string): string {
	return localDigits(raw).slice(0, 10);
}

export function normaliseMobile(raw: string): string | null {
	const digits = localDigits(raw);
	return /^[6-9]\d{9}$/.test(digits) ? digits : null;
}

// The same rule as the server (EmailVerificationService): something, an @, a domain with a dot and a
// top-level part of 2 or more; at most 254 characters. Quoted or IP-literal addresses are not accepted.
const EMAIL = /^[^\s@]{1,64}@[^\s@]+\.[^\s@.]{2,}$/;

/** The address as the server will keep it (trimmed, lower case), or null when it is not one. */
export function normaliseEmail(raw: string): string | null {
	const value = raw.trim().toLowerCase();
	return value.length <= 254 && EMAIL.test(value) ? value : null;
}

export function isPersonalPan(raw: string): boolean {
	return /^[A-Z]{3}P[A-Z]\d{4}[A-Z]$/.test(raw.trim().toUpperCase());
}

export function isPincode(raw: string): boolean {
	return /^[1-9]\d{5}$/.test(raw.trim());
}

export const MB = 1024 * 1024;

export interface FileRule {
	types: string[];
	maxBytes: number;
	label: string;
	/** How many files of this kind the server keeps (more than one only for computer certificates) */
	maxCount?: number;
}

/** One kind's rule as the server states it (GET /v1/partner/document-rules). */
export interface ServerRule {
	kind: string;
	formats: string[];
	max_bytes: number;
	min_side_px: number;
	max_count: number;
}

/** The server's rule in the applicant's words, e.g. "JPG, up to 5 MB, at least 300 × 300 px". */
export function ruleFrom(server: ServerRule): FileRule {
	// Pictures first, as everywhere else on the pages: "JPG or PDF"
	const names = server.formats.map((type) => (type === "application/pdf" ? "PDF" : type === "image/jpeg" ? "JPG" : type)).sort((a, b) => (a === "JPG" ? -1 : b === "JPG" ? 1 : 0));
	const size = `up to ${Math.floor(server.max_bytes / MB)} MB`;
	const side = server.min_side_px > 0 ? `, at least ${server.min_side_px} × ${server.min_side_px} px` : "";
	return { types: server.formats, maxBytes: server.max_bytes, maxCount: server.max_count, label: `${names.join(" or ")}, ${size}${side}` };
}

// Used until the server's rules have loaded (and on the start page, before sign-in); a test keeps them
// equal to the server's
export const PHOTO: FileRule = { types: ["image/jpeg"], maxBytes: 5 * MB, maxCount: 1, label: "JPG, up to 5 MB, at least 300 × 300 px" };
export const PAPER: FileRule = { types: ["image/jpeg", "application/pdf"], maxBytes: 10 * MB, maxCount: 1, label: "JPG or PDF, up to 10 MB" };

/** @return a message for the person, or null when the file may be sent */
export function checkFile(file: File, rule: FileRule): string | null {
	if (!rule.types.includes(file.type)) {
		return rule.types.length === 1 ? "Only JPG photos are accepted." : "Only JPG or PDF files are accepted.";
	}
	if (file.size > rule.maxBytes) {
		return `The file is larger than ${rule.maxBytes / MB} MB.`;
	}
	return null;
}
