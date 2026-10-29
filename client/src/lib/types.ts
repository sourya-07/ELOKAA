/** Shapes the server sends (docs/API.md). */

export type StepKey =
	| "MOBILE_VERIFICATION"
	| "EMAIL_VERIFICATION"
	| "CONSENT"
	| "AADHAAR"
	| "PAN"
	| "CREDIT_CHECK"
	| "HOME_ADDRESS"
	| "PHOTOS"
	| "EDUCATION"
	| "ALTERNATE_ID"
	| "KYC_DOCUMENTS"
	| "DECLARATION";

export type StepStatus =
	| "NOT_STARTED"
	| "IN_PROGRESS"
	| "PENDING_EXTERNAL"
	| "COMPLETED"
	| "FAILED"
	| "BLOCKED"
	| "LOCKED"
	| "REOPENED"
	| "NOT_APPLICABLE";

export type ApplicationStatus =
	| "IN_PROGRESS"
	| "INELIGIBLE"
	| "SUBMITTED"
	| "UNDER_REVIEW"
	| "CORRECTION_REQUIRED"
	| "ON_HOLD"
	| "APPROVED"
	| "REJECTED"
	| "WITHDRAWN"
	| "EXPIRED";

export interface Step {
	step_key: StepKey;
	status: StepStatus;
	attempt_count: number;
	max_attempts: number;
	last_error_code?: string;
	summary?: Record<string, unknown>;
	reopen_reason_code?: string;
	reviewer_message?: string;
}

export interface Snapshot {
	application_id: string;
	/** The 10-digit number people read and quote; the API and URLs use application_id. */
	application_number: string | null;
	/** The verified mobile and email, masked by the server (never the full values). */
	contact?: { mobile_masked: string | null; email_masked: string | null };
	status: ApplicationStatus;
	version: number;
	method: "DIGILOCKER" | "MANUAL" | null;
	next_step: StepKey | null;
	expires_at: string;
	submitted_at: string | null;
	steps: Step[];
	available_actions: string[];
	/** The file checklist (server rules) */
	requirements?: Requirement[];
	/** What still stops submission; empty when the applicant can submit */
	submit_blockers?: SubmitBlocker[];
}

export interface Requirement {
	group: string;
	step_key: StepKey;
	rule: "ALL" | "ONE_OF";
	options: string[][];
	satisfied: boolean;
}

export interface SubmitBlocker {
	step_key: StepKey;
	code: string;
	group?: string;
}

export interface DocumentView {
	document_id: string;
	kind: string;
	status: string;
	file_name: string;
	content_type: string;
	size: number;
	reject_reason?: string;
	uploaded_at: string;
	/** Live photo only: how precise the recorded location was (never the coordinates) */
	location?: { accuracy_m: number; flags: string };
}

export const SATISFIED: StepStatus[] = ["COMPLETED", "LOCKED", "NOT_APPLICABLE"];

export function isDone(step: Step | undefined): boolean {
	return !!step && SATISFIED.includes(step.status);
}
