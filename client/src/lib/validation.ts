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
}

export const PHOTO: FileRule = { types: ["image/jpeg"], maxBytes: 5 * MB, label: "JPG, up to 5 MB" };
export const PAPER: FileRule = { types: ["image/jpeg", "application/pdf"], maxBytes: 10 * MB, label: "JPG or PDF, up to 10 MB" };

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
