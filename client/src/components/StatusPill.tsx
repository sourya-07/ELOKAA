import { Badge } from "@chakra-ui/react";

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

/** A status as a pill: words, not only colour. */
export function StatusPill({ status }: { status: string }) {
	const tone = TONES[status] ?? { bg: "bg.subtle", color: "text.muted", label: status.toLowerCase().replace(/_/g, " ") };
	return (
		<Badge bg={tone.bg} color={tone.color} borderRadius="full" px={2.5} py={0.5} textTransform="none" fontWeight={500}>
			{tone.label}
		</Badge>
	);
}
