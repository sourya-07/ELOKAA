import { Box, Button, Collapse, Flex, Heading, Stack, Text, usePrefersReducedMotion } from "@chakra-ui/react";
import { type ReactNode, useEffect, useId, useRef, useState } from "react";
import { StatusIcon, type StatusTone } from "@/components/StatusIcon";
import { enter, useTurnedOn } from "@/styles/motion";

export type TaskState = "done" | "current" | "todo" | "locked" | "blocked" | "attention";

interface Props {
	/** Anchor for links such as "Fix" on the review page */
	id: string;
	title: string;
	state: TaskState;
	/** One line shown once the task is done (masked values only) */
	summary?: ReactNode;
	/** Why the task cannot be started yet, e.g. "After your PAN is verified" */
	lockedReason?: string;
	/** A done task can be opened again to change it (the server still decides if it may) */
	canChange?: boolean;
	/** The application version: a done task opened with "Change" folds again once a save moves it on */
	version?: number;
	children: ReactNode;
}

const MARK: Record<TaskState, { label: string; color: string; bg: string; tone: StatusTone }> = {
	done: { label: "Done", color: "status.ok.fg", bg: "status.ok.bg", tone: "ok" },
	current: { label: "To do now", color: "connect.500", bg: "connect.50", tone: "current" },
	todo: { label: "Not started", color: "text.muted", bg: "bg.subtle", tone: "neutral" },
	locked: { label: "Later", color: "text.muted", bg: "bg.subtle", tone: "neutral" },
	blocked: { label: "Needs our team", color: "status.error.fg", bg: "status.error.bg", tone: "error" },
	attention: { label: "Needs a change", color: "status.warn.fg", bg: "status.warn.bg", tone: "attention" },
};

/**
 * One task of a page that holds several (identity: Aadhaar, PAN, eligibility, ID). The task to do
 * now is open; done tasks fold to a one-line summary; later tasks say why they wait. Opening a
 * task moves focus to its heading so keyboard and screen-reader users land in the right place.
 */
export function TaskSection({ id, title, state, summary, lockedReason, canChange, version, children }: Props) {
	const bodyId = useId();
	const heading = useRef<HTMLHeadingElement>(null);
	const reduced = usePrefersReducedMotion();
	const openByState = state === "current" || state === "attention" || state === "blocked";
	const [open, setOpen] = useState(openByState);
	const wasOpen = useRef(open);

	// The server moved the task on (done, or now current): follow it
	useEffect(() => setOpen(openByState), [openByState]);
	// A done task opened with "Change" stays done after a save, so its state does not move: a new
	// version from the server (the save went through) folds it to show the saved result
	const lastVersion = useRef(version);
	useEffect(() => {
		if (version !== lastVersion.current && state === "done") {
			setOpen(false);
		}
		lastVersion.current = version;
	}, [version, state]);
	useEffect(() => {
		if (open && !wasOpen.current) {
			heading.current?.focus();
		}
		wasOpen.current = open;
	}, [open]);

	// Only a change animates: a panel opened (by the applicant or because the server moved on) rises
	// into place, and a mark that turns to "Done" grows in; nothing moves on the first render
	const opened = useTurnedOn(open);
	const finished = useTurnedOn(state === "done");

	const mark = MARK[state];
	const body = (
		<Box id={bodyId} pt={4} sx={opened ? enter(0, 200) : undefined}>
			<Stack spacing={4}>{children}</Stack>
		</Box>
	);
	return (
		<Box
			as="section"
			id={id}
			aria-labelledby={`${bodyId}-title`}
			bg="bg.surface"
			border="1px solid"
			borderColor={state === "current" ? "ink.300" : "border.hairline"}
			borderRadius="card"
			p={{ base: 4, md: 6 }}
		>
			<Flex justify="space-between" align="flex-start" gap={3}>
				<Heading as="h2" size="sm" id={`${bodyId}-title`} ref={heading} tabIndex={-1} minW={0} _focus={{ outline: "none" }} _focusVisible={{ outline: "2px solid", outlineColor: "connect.500", outlineOffset: "4px" }}>
					{title}
				</Heading>
				<Flex align="center" gap={3} flexShrink={0}>
					<Flex as="span" role="status" align="center" gap={1} data-turned={finished ? "ok" : undefined} fontSize="xs" fontWeight={600} px={2} py={0.5} borderRadius="full" color={mark.color} bg={mark.bg}>
						<StatusIcon tone={mark.tone} />
						{mark.label}
					</Flex>
					{state === "done" && canChange && (
						<Button variant="link" size="sm" aria-expanded={open} aria-controls={bodyId} onClick={() => setOpen(!open)}>
							{open ? "Close" : "Change"}
						</Button>
					)}
				</Flex>
			</Flex>
			{/* Its own line under the title, full width: a masked number is not squeezed beside the status */}
			{!open && state === "done" && summary && (
				<Text fontSize="sm" color="text.muted" mt={1} sx={{ fontVariantNumeric: "tabular-nums" }}>
					{summary}
				</Text>
			)}
			{!open && state === "locked" && lockedReason && (
				<Text fontSize="sm" color="text.muted" mt={1}>
					{lockedReason}
				</Text>
			)}
			{reduced ? open && body : <Collapse in={open} animateOpacity unmountOnExit>{body}</Collapse>}
		</Box>
	);
}
