import type { Snapshot, StepKey } from "@/lib/types";

/** Where each step is done (docs/PARTNER_JOURNEY.md). One page can hold several steps, each with its own anchor. */
export interface Page {
	path: string;
	title: string;
	step: StepKey;
}

export const PAGES: Page[] = [
	{ path: "/apply", title: "Contact details", step: "EMAIL_VERIFICATION" },
	{ path: "/apply/identity#aadhaar", title: "Aadhaar (DigiLocker)", step: "AADHAAR" },
	{ path: "/apply/identity#address", title: "Home address", step: "HOME_ADDRESS" },
	{ path: "/apply/identity#pan", title: "PAN", step: "PAN" },
	{ path: "/apply/identity#eligibility", title: "Eligibility", step: "CREDIT_CHECK" },
	{ path: "/apply/identity#alternate-id", title: "Voter ID or driving licence", step: "ALTERNATE_ID" },
	{ path: "/apply/photos", title: "Photos", step: "PHOTOS" },
	{ path: "/apply/education", title: "Education", step: "EDUCATION" },
	{ path: "/apply/id-document", title: "Voter ID or driving licence images", step: "ID_DOCUMENT" },
	{ path: "/apply/review", title: "Review", step: "DECLARATION" },
];

const IDENTITY: StepKey[] = ["AADHAAR", "HOME_ADDRESS", "PAN", "CREDIT_CHECK", "ALTERNATE_ID"];

/** Where the applicant continues: the page of the server's next step. */
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
	if (IDENTITY.includes(next)) {
		return "/apply/identity";
	}
	return PAGES.find((page) => page.step === next)?.path ?? "/apply/review";
}

/** A return address inside the journey only (never another site). */
export function safeNext(value: unknown): string | null {
	return typeof value === "string" && value.startsWith("/apply/") && !value.startsWith("//") ? value : null;
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
	DIGILOCKER_NO_AADHAAR_DATA: "Your DigiLocker has no Aadhaar linked. Link your Aadhaar in DigiLocker, then try again.",
	DIGILOCKER_UNAVAILABLE: "DigiLocker is not answering right now. Nothing was used up; check again in a few minutes.",
	DIGILOCKER_UNDER_AGE: "You must be 18 or older to become an ELOKA partner.",
	AADHAAR_NOT_VERIFIED: "Verify your Aadhaar through DigiLocker first.",
	VOTER_ID_NOT_FOUND: "No voter ID with this number was found. Check the number on your card.",
	VOTER_ID_NAME_MISMATCH: "The name on this voter ID does not match your Aadhaar.",
	VOTER_ID_DOB_MISMATCH: "The date of birth on this voter ID does not match your Aadhaar.",
	DRIVING_LICENCE_NOT_FOUND: "No driving licence with this number was found. Check the number on your licence.",
	DRIVING_LICENCE_NAME_MISMATCH: "The name on this driving licence does not match your Aadhaar.",
	DRIVING_LICENCE_DOB_MISMATCH: "The date of birth on this driving licence does not match your Aadhaar.",
	DRIVING_LICENCE_NOT_ACTIVE: "This driving licence is not active (for example, it has expired).",
	ID_ALREADY_USED: "This document is already used in another application.",
	ALTERNATE_ID_UNAVAILABLE: "The government record could not be checked right now. Nothing was used up; please try again in a few minutes.",
};
