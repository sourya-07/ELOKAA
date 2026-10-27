/** Checks done in the browser for quick feedback. The server checks everything again. */

export function normaliseMobile(raw: string): string | null {
	let digits = raw.replace(/\D/g, "");
	if (digits.length === 12 && digits.startsWith("91")) {
		digits = digits.slice(2);
	} else if (digits.length === 11 && digits.startsWith("0")) {
		digits = digits.slice(1);
	}
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
