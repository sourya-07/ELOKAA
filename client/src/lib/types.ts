/** Shapes the server sends (docs/API.md). */

export type StepKey =
	| "MOBILE_VERIFICATION"
	| "CONSENT"
	| "VERIFICATION_METHOD"
	| "AADHAAR"
	| "PAN"
	| "CREDIT_CHECK"
	| "HOME_ADDRESS"
	| "PHOTOS"
	| "EDUCATION"
	| "ID_DOCUMENT"
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
	status: ApplicationStatus;
	version: number;
	method: "DIGILOCKER" | "MANUAL" | null;
	next_step: StepKey | null;
	expires_at: string;
	submitted_at: string | null;
	steps: Step[];
	available_actions: string[];
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
}

export const SATISFIED: StepStatus[] = ["COMPLETED", "LOCKED", "NOT_APPLICABLE"];

export function isDone(step: Step | undefined): boolean {
	return !!step && SATISFIED.includes(step.status);
}
