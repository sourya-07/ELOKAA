import type { Snapshot } from "@/lib/types";

export interface StaffView {
	id: string;
	email_masked: string;
	display_name: string;
	roles: ("REVIEWER" | "APPROVER")[];
	active: boolean;
	environment?: string;
	/** Fake identity checks may run here (local, test): evidence is not from a real provider */
	test_providers?: boolean;
}

export interface QueueItem {
	application_id: string;
	application_number: string | null;
	status: string;
	method: string | null;
	submitted_at: string | null;
	assigned_to: string | null;
	waiting_items: number;
}

export interface EvidenceItem {
	id: string;
	type: "VERIFICATION" | "DOCUMENT";
	kind: string;
	status: string;
	value: string | null;
	reason_code: string | null;
}

export interface CaseView {
	application: Snapshot;
	assigned_to: string | null;
	assigned_to_id: string | null;
	eligibility: string;
	evidence: EvidenceItem[];
	address: Record<string, string>;
	id_numbers: Record<string, string>;
	actions: { action: string; staff: string; decision: string | null; reason_code: string | null; note: string | null; occurred_at: string }[];
	approval_blockers: string[];
	/** Steps a reviewer may send back (the server's list) */
	correctable_steps: string[];
	/** Where the live photo was taken: the browser's word, not proof */
	photo_location: { latitude: number; longitude: number; accuracy_m: number; captured_at: string; flags: string; approximate_address?: string | null; blink?: "CONFIRMED" | "UNCONFIRMED" | "NOT_CHECKED" | null } | null;
}
