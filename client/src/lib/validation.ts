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
	/** Accepted types, as the server detects them from the bytes */
	types: string[];
	minBytes: number;
	maxBytes: number;
	label: string;
	/** How many files of this kind the server keeps (more than one only for computer certificates) */
	maxCount?: number;
}

/** One kind's rule as the server states it (GET /v1/partner/document-rules). */
export interface ServerRule {
	kind: string;
	formats: string[];
	min_bytes: number;
	max_bytes: number;
	min_side_px: number;
	max_count: number;
}

const KB = 1024;

/** "10 KB", "2 MB": how the limits are said to the applicant (the server's words too). */
export function sizeText(bytes: number): string {
	return bytes >= MB ? `${Math.floor(bytes / MB)} MB` : `${Math.floor(bytes / KB)} KB`;
}

function formatNames(types: string[]): string {
	return types.includes("application/pdf") ? "JPG, PNG or PDF" : "JPG or PNG";
}

/** The server's rule in the applicant's words, e.g. "JPG or PNG, 20 KB to 2 MB, at least 300 × 300 px". */
export function ruleFrom(server: ServerRule): FileRule {
	const side = server.min_side_px > 0 ? `, at least ${server.min_side_px} × ${server.min_side_px} px` : "";
	return {
		types: server.formats,
		minBytes: server.min_bytes,
		maxBytes: server.max_bytes,
		maxCount: server.max_count,
		label: `${formatNames(server.formats)}, ${sizeText(server.min_bytes)} to ${sizeText(server.max_bytes)}${side}`,
	};
}

// Used until the server's rules have loaded (and on the start page, before sign-in); a test keeps them
// equal to the server's (DocumentKind)
export const PHOTO: FileRule = {
	types: ["image/jpeg", "image/png"],
	minBytes: 20 * KB,
	maxBytes: 2 * MB,
	maxCount: 1,
	label: "JPG or PNG, 20 KB to 2 MB, at least 300 × 300 px",
};
export const PAPER: FileRule = {
	types: ["application/pdf", "image/jpeg", "image/png"],
	minBytes: 10 * KB,
	maxBytes: 5 * MB,
	maxCount: 1,
	label: "JPG, PNG or PDF, 10 KB to 5 MB",
};

/** What the file input offers in the picker (the check itself reads the bytes). */
export function acceptOf(rule: FileRule): string {
	const extensions = rule.types.flatMap((type) => (type === "image/jpeg" ? [".jpg", ".jpeg"] : type === "image/png" ? [".png"] : type === "application/pdf" ? [".pdf"] : []));
	return [...rule.types, ...extensions].join(",");
}

function readBytes(blob: Blob): Promise<Uint8Array> {
	return new Promise((resolve, reject) => {
		const reader = new FileReader();
		reader.onload = () => resolve(new Uint8Array(reader.result as ArrayBuffer));
		reader.onerror = () => reject(reader.error);
		reader.readAsArrayBuffer(blob);
	});
}

/** The real type from the first bytes, never the name or the browser's guess (the server does the same). */
export function sniff(head: Uint8Array): string | null {
	if (head.length >= 3 && head[0] === 0xff && head[1] === 0xd8 && head[2] === 0xff) {
		return "image/jpeg";
	}
	if (head.length >= 8 && head[0] === 0x89 && head[1] === 0x50 && head[2] === 0x4e && head[3] === 0x47) {
		return "image/png";
	}
	if (head.length >= 5 && String.fromCharCode(...head.slice(0, 5)) === "%PDF-") {
		return "application/pdf";
	}
	return null;
}

function lastIndexOf(bytes: Uint8Array, needle: number[], from = 0): number {
	outer: for (let i = bytes.length - needle.length; i >= from; i--) {
		for (let j = 0; j < needle.length; j++) {
			if (bytes[i + j] !== needle[j]) {
				continue outer;
			}
		}
		return i;
	}
	return -1;
}

function jpegScanStart(bytes: Uint8Array): number {
	let pos = 2;
	while (pos + 4 <= bytes.length && bytes[pos] === 0xff) {
		if (bytes[pos + 1] === 0xda) {
			return pos;
		}
		const length = (bytes[pos + 2] << 8) | bytes[pos + 3];
		if (length < 2) {
			return -1;
		}
		pos += 2 + length;
	}
	return -1;
}

/** Whether the file reaches its proper end (same checks as the server's FileType.intact). */
export function intact(type: string, bytes: Uint8Array): boolean {
	if (type === "image/jpeg") {
		const scan = jpegScanStart(bytes);
		return scan > 0 && lastIndexOf(bytes, [0xff, 0xd9], scan) > scan;
	}
	if (type === "image/png") {
		return lastIndexOf(bytes, [0x49, 0x45, 0x4e, 0x44]) > 8; // IEND
	}
	if (type === "application/pdf") {
		const eof = [0x25, 0x25, 0x45, 0x4f, 0x46]; // %%EOF
		return lastIndexOf(bytes, eof, Math.max(0, bytes.length - 1024)) >= 0 && lastIndexOf(bytes, [0x20, 0x6f, 0x62, 0x6a]) > 0; // " obj"
	}
	return false;
}

/**
 * Checks a file before it is sent, with the server's rules and the server's messages: not empty, its real
 * type (from the bytes), at least the minimum and at most the maximum size, and complete (not cut off).
 * The server checks everything again, and also decodes pictures and scans PDFs.
 * @return a message for the person, or null when the file may be sent
 */
export async function checkFile(file: File, rule: FileRule): Promise<string | null> {
	if (file.size === 0) {
		return "The file is empty. Choose the file again.";
	}
	const type = sniff(await readBytes(file.slice(0, 16)));
	if (!type || !rule.types.includes(type)) {
		return `Only ${formatNames(rule.types)} files are accepted.`;
	}
	if (file.size < rule.minBytes) {
		return `The file is smaller than ${sizeText(rule.minBytes)}. It may be blank or damaged: upload a clear photo or scan.`;
	}
	if (file.size > rule.maxBytes) {
		return `The file is larger than ${sizeText(rule.maxBytes)}. Upload a smaller file.`;
	}
	if (!intact(type, await readBytes(file))) {
		return "This file is damaged or incomplete. Save it again and upload it.";
	}
	return null;
}
