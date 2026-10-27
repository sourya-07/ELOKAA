import type { Snapshot } from "@/lib/types";

export interface StaffView {
	id: string;
	email_masked: string;
	display_name: string;
	roles: ("REVIEWER" | "APPROVER")[];
	active: boolean;
}

export interface QueueItem {
	application_id: string;
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
}
