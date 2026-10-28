import { Badge } from "@chakra-ui/react";
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

/** A status as a pill: words, not only colour. Staff see review states; applicants see their own words. */
export function StatusPill({ status, forApplicant }: { status: string; forApplicant?: boolean }) {
	const turned = useTurnedOn(status === "VERIFIED" || status === "COMPLETED");
	const tone = (forApplicant ? APPLICANT[status] : undefined) ?? TONES[status] ?? { bg: "bg.subtle", color: "text.muted", label: status.toLowerCase().replace(/_/g, " ") };
	return (
		// keyed by status: in the journey a change fades in, and one to verified or done grows in a
		// little (journeyFeedback); staff see no motion
		<Badge key={status} className="status-pill" data-turned={turned ? "ok" : undefined} bg={tone.bg} color={tone.color} borderRadius="full" px={2.5} py={0.5} textTransform="none" fontWeight={500}>
			{tone.label}
		</Badge>
	);
}
