import type { Snapshot, StepKey } from "@/lib/types";

/** Pages of the journey (docs/PARTNER_JOURNEY.md) and the step each one completes. */
export interface Page {
	path: string;
	title: string;
	step: StepKey;
	paths: ("DIGILOCKER" | "MANUAL")[];
}

export const PAGES: Page[] = [
	{ path: "/apply", title: "Mobile number", step: "MOBILE_VERIFICATION", paths: ["DIGILOCKER", "MANUAL"] },
	{ path: "/apply/method", title: "How to apply", step: "VERIFICATION_METHOD", paths: ["DIGILOCKER", "MANUAL"] },
	{ path: "/apply/digilocker", title: "DigiLocker", step: "AADHAAR", paths: ["DIGILOCKER"] },
	{ path: "/apply/aadhaar", title: "Aadhaar", step: "AADHAAR", paths: ["MANUAL"] },
	{ path: "/apply/pan", title: "PAN", step: "PAN", paths: ["DIGILOCKER", "MANUAL"] },
	{ path: "/apply/credit-check", title: "Credit check", step: "CREDIT_CHECK", paths: ["DIGILOCKER", "MANUAL"] },
	{ path: "/apply/address", title: "Home address", step: "HOME_ADDRESS", paths: ["MANUAL"] },
	{ path: "/apply/photos", title: "Photos", step: "PHOTOS", paths: ["DIGILOCKER", "MANUAL"] },
	{ path: "/apply/education", title: "Education", step: "EDUCATION", paths: ["DIGILOCKER", "MANUAL"] },
	{ path: "/apply/id-document", title: "Voter ID or driving licence", step: "ID_DOCUMENT", paths: ["DIGILOCKER", "MANUAL"] },
	{ path: "/apply/review", title: "Review", step: "DECLARATION", paths: ["DIGILOCKER", "MANUAL"] },
];

/**
 * The pages of the applicant's path, in order. On the DigiLocker path the PAN is entered on the
 * DigiLocker page and the home address comes from DigiLocker, so those pages are not separate steps.
 */
export function pagesFor(method: Snapshot["method"]): Page[] {
	const path = method ?? "MANUAL";
	return PAGES.filter((page) => page.paths.includes(path)).filter(
		(page) => !(path === "DIGILOCKER" && page.path === "/apply/pan"),
	);
}

/** Where the applicant continues: the server's next step, shown on its page. */
export function routeFor(snapshot: Snapshot): string {
	if (!["IN_PROGRESS", "CORRECTION_REQUIRED"].includes(snapshot.status)) {
		return "/apply/status";
	}
	const next = snapshot.next_step;
	if (!next || next === "MOBILE_VERIFICATION") {
		return "/apply/review";
	}
	if (next === "EMAIL_VERIFICATION" || next === "CONSENT") {
		return "/apply"; // contact details: the sign-in page continues with the email
	}
	if (next === "VERIFICATION_METHOD") {
		return "/apply/method";
	}
	if (next === "AADHAAR") {
		return snapshot.method === "DIGILOCKER" ? "/apply/digilocker" : "/apply/aadhaar";
	}
	if (next === "PAN" && snapshot.method === "DIGILOCKER") {
		return "/apply/digilocker";
	}
	if (next === "HOME_ADDRESS") {
		return "/apply/address"; // on the DigiLocker path only when DigiLocker gave no full address
	}
	return PAGES.find((page) => page.step === next && page.paths.includes(snapshot.method ?? "MANUAL"))?.path ?? "/apply/review";
}

/** What a step error code means for the applicant. */
export const ERROR_TEXT: Record<string, string> = {
	PAN_NAME_MISMATCH: "The name does not match the PAN records. Use your name exactly as on your PAN card.",
	PAN_DOB_MISMATCH: "The date of birth does not match the PAN records.",
	PAN_INVALID_PAN: "This PAN is not valid. Check the number and try again.",
	PAN_ALREADY_USED: "This PAN is already used in another application.",
	BUREAU_UNAVAILABLE: "We could not complete the credit check. Please try again later.",
	NO_CREDIT_HISTORY: "We could not find a credit history for you. Our team will contact you.",
	INVALID_SCORE: "We could not complete the credit check. Our team will contact you.",
	CREDIT_SCORE_BELOW_THRESHOLD: "Your credit check did not meet our requirement.",
	DIGILOCKER_INVALID_REFERENCE: "DigiLocker did not finish. Please try again.",
};
