import type { Problem } from "@/lib/api";
import { missingText } from "@/lib/documents";
import { waitText } from "@/lib/format";

type Message = string | ((problem: Problem) => string);

const RELOAD = "Something in the request was not right. Please reload the page and try again.";

/**
 * What the applicant (or staff member) reads for each error code the server can send
 * (docs/ERROR_CATALOG.md; a test keeps the two in step). The client's own words, so they read
 * the same everywhere and can be translated; the server's detail is only the fallback for a code
 * missing here. Step results (last_error_code) are worded in lib/steps.ts ERROR_TEXT.
 */
export const ERRORS: Record<string, Message> = {
	VALIDATION_FAILED: "Check the details you entered.",
	MALFORMED_REQUEST: RELOAD,
	METHOD_NOT_ALLOWED: RELOAD,
	UNSUPPORTED_MEDIA_TYPE: RELOAD,
	NOT_ACCEPTABLE: RELOAD,
	REQUEST_REJECTED: RELOAD,
	IDEMPOTENCY_KEY_REQUIRED: RELOAD,
	IDEMPOTENCY_KEY_REUSED: RELOAD,
	PRECONDITION_REQUIRED: RELOAD,
	PRECONDITION_FAILED: "Your application changed in another window. The latest version is shown.",
	UNAUTHENTICATED: "Please sign in again.",
	SESSION_EXPIRED: "Your session has ended. Please sign in again.",
	FORBIDDEN: "You are not allowed to do this.",
	NOT_STAFF: "This account is not an ELOKA staff account.",
	SAME_PERSON: "The person who recommended cannot also approve.",
	NOT_ASSIGNED: "Claim the case first.",
	OTP_INVALID: (p) => (p.attempts_left !== undefined ? `The code is not correct. ${p.attempts_left} attempts left.` : "The code is not correct."),
	OTP_EXPIRED: "This code has expired, or was already used. Ask for a new one.",
	OTP_LOCKED: "Too many wrong codes. Ask for a new code.",
	OTP_LIMIT_REACHED: (p) => (p.retry_after ? `Too many codes asked for. You can ask again in ${waitText(Number(p.retry_after))}.` : "Too many codes asked for. Please try again later."),
	DIGILOCKER_LIMIT_REACHED: "DigiLocker was started too many times. Please try again in an hour.",
	APPLICATION_NOT_FOUND: "We could not find this application.",
	NO_APPLICATION: "You have not started an application yet.",
	APPLICANT_NOT_FOUND: "Please sign in again.",
	DOCUMENT_NOT_FOUND: "This file is no longer available.",
	NOT_FOUND: "Not found.",
	STEP_NOT_FOUND: "This application has no such step.",
	ADDRESS_NOT_FOUND: "This application has no address yet.",
	EVIDENCE_NOT_FOUND: "This check was not found.",
	NOTHING_TO_SHOW: "There is no value to show.",
	FILE_TOO_LARGE: "The file is too large. Files can be at most 10 MB.",
	DUPLICATE_FILE: "You already uploaded this same file for another document. Choose the right file.",
	LOCATION_REQUIRED: "The live photo needs your location. Allow location for this site and take the photo again.",
	LOCATION_INVALID: "We could not read your location. Take the photo again.",
	LOCATION_TOO_INACCURATE: (p) =>
		`Your location is too rough${p.accuracy_m ? ` (about ${p.accuracy_m} m)` : ""}. Turn on precise location or GPS, then take the photo again.`,
	LOCATION_STALE: "This location is not from now. Take the photo again.",
	STEP_BLOCKED: "Too many failed attempts on this step. Our team will contact you.",
	CONFLICT: "This was changed at the same moment somewhere else. Please reload and try again.",
	ALREADY_APPROVED: "You are already an approved ELOKA partner.",
	APPLICATION_NOT_EDITABLE: "Your application can no longer be changed.",
	STEP_NOT_EDITABLE: "This part cannot be changed now.",
	INVALID_STATE_TRANSITION: "This cannot be done in the application's current status.",
	PREREQUISITE_INCOMPLETE: "Finish the earlier steps first.",
	CONSENT_REQUIRED: "Tick the consent box first.",
	CONSENT_VERSION_OUTDATED: "The consent text changed. Please reload and read it again.",
	DECLARATION_VERSION_OUTDATED: "The declaration changed. Please reload and read it again.",
	NOT_ELIGIBLE: "The credit check has not been passed.",
	DOCUMENTS_MISSING: (p) => (p.missing_documents?.length ? missingText(p.missing_documents) : "Some required files are missing."),
	DOCUMENT_REQUIRED: "This file is required. Upload a new one to replace it.",
	TOO_MANY_DOCUMENTS: "You can add up to 5 computer certificates.",
	EMAIL_ALREADY_USED: "Please use a different email address.",
	ADDRESS_FROM_DIGILOCKER: "Your address came from DigiLocker and cannot be typed over.",
	ID_TYPE_EXHAUSTED: "No tries are left for this document. Use the other one.",
	AADHAAR_NOT_VERIFIED: "Verify your Aadhaar through DigiLocker first.",
	DIGILOCKER_ALREADY_USED: "This DigiLocker link was already used.",
	DIGILOCKER_EXPIRED: "This DigiLocker link has expired. Please start again.",
	IDEMPOTENCY_IN_PROGRESS: "This is still being done. Please wait a moment.",
	STEP_NOT_BLOCKED: "This step is not blocked.",
	EVIDENCE_NOT_DECIDABLE: "This check cannot be decided by hand.",
	NOT_READY: "Some items are not verified yet.",
	NO_RECOMMENDATION: "A reviewer must recommend approval first.",
	PROVIDER_NOT_CONFIGURED: "This check is not available right now. Our team has been told.",
	PROVIDER_UNAVAILABLE: "A service we use is not answering. Nothing was used up; please try again in a few minutes.",
	INTERNAL_ERROR: "Something went wrong on our side. Please try again.",
	// Made by this app, not the server
	NETWORK: "No connection. Check your internet and try again.",
	SERVER_UNAVAILABLE: "We could not reach ELOKA. Please try again in a moment.",
	ABORTED: "Upload cancelled.",
};

/** The client's words for a problem, or null for a code it does not know. */
export function errorMessage(problem: Problem): string | null {
	const message = ERRORS[problem.code];
	return message === undefined ? null : typeof message === "function" ? message(problem) : message;
}
