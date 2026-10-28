import type { Snapshot, StepKey } from "@/lib/types";
import { isDone } from "@/lib/types";

/**
 * The parts of the application as the applicant thinks of them. Progress counts finished parts
 * (from the server's step statuses), not screens: one page can finish several steps, and a step
 * can need several visits.
 */
export interface Milestone {
	key: "CONTACT" | "IDENTITY" | "ELIGIBILITY" | "PHOTOS" | "DOCUMENTS" | "REVIEW";
	label: string;
	steps: StepKey[];
}

export const MILESTONES: Milestone[] = [
	{ key: "CONTACT", label: "Contact details", steps: ["MOBILE_VERIFICATION", "EMAIL_VERIFICATION", "CONSENT"] },
	{ key: "IDENTITY", label: "Identity", steps: ["AADHAAR", "HOME_ADDRESS", "PAN", "ALTERNATE_ID"] },
	{ key: "ELIGIBILITY", label: "Eligibility", steps: ["CREDIT_CHECK"] },
	{ key: "PHOTOS", label: "Photos", steps: ["PHOTOS"] },
	{ key: "DOCUMENTS", label: "Documents", steps: ["KYC_DOCUMENTS", "EDUCATION"] },
	{ key: "REVIEW", label: "Review and submit", steps: ["DECLARATION"] },
];

export interface MilestoneState extends Milestone {
	done: boolean;
	current: boolean;
}

/** Each part with whether it is finished and whether the applicant is in it now. */
export function milestonesOf(snapshot: Snapshot): MilestoneState[] {
	const submitted = !["IN_PROGRESS", "CORRECTION_REQUIRED"].includes(snapshot.status);
	return MILESTONES.map((milestone) => {
		// Steps of another journey version are not in this application: they do not count
		const steps = snapshot.steps.filter((step) => milestone.steps.includes(step.step_key));
		const done = steps.length > 0 && steps.every(isDone) && (milestone.key !== "REVIEW" || submitted);
		const current = !done && !!snapshot.next_step && milestone.steps.includes(snapshot.next_step);
		return { ...milestone, done, current };
	});
}

export function progressOf(snapshot: Snapshot): { done: number; total: number } {
	const states = milestonesOf(snapshot);
	return { done: states.filter((state) => state.done).length, total: states.length };
}
