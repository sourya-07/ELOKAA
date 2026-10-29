import { Badge } from "@chakra-ui/react";
import { StatusIcon, type StatusTone } from "@/components/StatusIcon";
import { useTurnedOn } from "@/styles/motion";

const TONES: Record<string, { bg: string; color: string; label: string }> = {
	VERIFIED: { bg: "status.ok.bg", color: "status.ok.fg", label: "Verified" },
	COMPLETED: { bg: "status.ok.bg", color: "status.ok.fg", label: "Done" },
	LOCKED: { bg: "status.ok.bg", color: "status.ok.fg", label: "Submitted" },
	MANUAL_REVIEW_REQUIRED: { bg: "status.warn.bg", color: "status.warn.fg", label: "Waiting for review" },
	SUBMITTED: { bg: "status.warn.bg", color: "status.warn.fg", label: "Waiting for review" },
	PENDING_EXTERNAL: { bg: "status.warn.bg", color: "status.warn.fg", label: "Waiting" },
	REOPENED: { bg: "status.warn.bg", color: "status.warn.fg", label: "Needs changes" },
	IN_PROGRESS: { bg: "bg.subtle", color: "text.muted", label: "In progress" },
	NOT_STARTED: { bg: "bg.subtle", color: "text.muted", label: "Not started" },
	REJECTED: { bg: "status.error.bg", color: "status.error.fg", label: "Rejected" },
	FAILED: { bg: "status.error.bg", color: "status.error.fg", label: "Failed" },
	BLOCKED: { bg: "status.error.bg", color: "status.error.fg", label: "Blocked" },
	VERIFICATION_FAILED: { bg: "status.error.bg", color: "status.error.fg", label: "Failed" },
};

/** What an applicant reads for a file they just added: it is theirs, not yet anyone's to review. */
const APPLICANT: Record<string, { bg: string; color: string; label: string }> = {
	MANUAL_REVIEW_REQUIRED: { bg: "status.ok.bg", color: "status.ok.fg", label: "Uploaded" },
	SUBMITTED: { bg: "status.ok.bg", color: "status.ok.fg", label: "Uploaded" },
};

/** The mark for a pill, from its colour family: the same meaning, not only the colour. */
function toneOf(bg: string, status: string): StatusTone {
	if (bg === "status.ok.bg") {
		return "ok";
	}
	if (bg === "status.error.bg") {
		return "error";
	}
	if (bg === "status.warn.bg") {
		return status === "REOPENED" ? "attention" : "warn";
	}
	return status === "IN_PROGRESS" ? "current" : "neutral";
}

/** A status as a pill: words, not only colour. Staff see review states; applicants see their own words. */
export function StatusPill({ status, forApplicant }: { status: string; forApplicant?: boolean }) {
	const turned = useTurnedOn(status === "VERIFIED" || status === "COMPLETED");
	const tone = (forApplicant ? APPLICANT[status] : undefined) ?? TONES[status] ?? { bg: "bg.subtle", color: "text.muted", label: status.toLowerCase().replace(/_/g, " ") };
	return (
		// keyed by status: in the journey a change fades in, and one to verified or done grows in a
		// little (journeyFeedback); staff see no motion
		<Badge key={status} className="status-pill" data-turned={turned ? "ok" : undefined} bg={tone.bg} color={tone.color} borderRadius="full" px={2.5} py={0.5} textTransform="none" fontWeight={500} display="inline-flex" alignItems="center" gap={1}>
			<StatusIcon tone={toneOf(tone.bg, status)} />
			{tone.label}
		</Badge>
	);
}
