import { Box, Button, Collapse, Flex, Heading, Stack, Text, usePrefersReducedMotion } from "@chakra-ui/react";
import { type ReactNode, useEffect, useId, useRef, useState } from "react";

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
	children: ReactNode;
}

const MARK: Record<TaskState, { label: string; color: string; bg: string }> = {
	done: { label: "Done", color: "status.ok.fg", bg: "status.ok.bg" },
	current: { label: "To do now", color: "connect.500", bg: "connect.50" },
	todo: { label: "Not started", color: "text.muted", bg: "bg.subtle" },
	locked: { label: "Later", color: "text.muted", bg: "bg.subtle" },
	blocked: { label: "Needs our team", color: "status.error.fg", bg: "status.error.bg" },
	attention: { label: "Needs a change", color: "status.warn.fg", bg: "status.warn.bg" },
};

/**
 * One task of a page that holds several (identity: Aadhaar, PAN, eligibility, ID). The task to do
 * now is open; done tasks fold to a one-line summary; later tasks say why they wait. Opening a
 * task moves focus to its heading so keyboard and screen-reader users land in the right place.
 */
export function TaskSection({ id, title, state, summary, lockedReason, canChange, children }: Props) {
	const bodyId = useId();
	const heading = useRef<HTMLHeadingElement>(null);
	const reduced = usePrefersReducedMotion();
	const openByState = state === "current" || state === "attention" || state === "blocked";
	const [open, setOpen] = useState(openByState);
	const wasOpen = useRef(open);

	// The server moved the task on (done, or now current): follow it
	useEffect(() => setOpen(openByState), [openByState]);
	useEffect(() => {
		if (open && !wasOpen.current) {
			heading.current?.focus();
		}
		wasOpen.current = open;
	}, [open]);

	const mark = MARK[state];
	const body = (
		<Box id={bodyId} pt={4}>
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
				<Stack spacing={1} minW={0}>
					<Heading as="h2" size="sm" id={`${bodyId}-title`} ref={heading} tabIndex={-1} _focus={{ outline: "none" }}>
						{title}
					</Heading>
					{!open && state === "done" && summary && (
						<Text fontSize="sm" color="text.muted" fontFamily="mono">
							{summary}
						</Text>
					)}
					{!open && state === "locked" && lockedReason && (
						<Text fontSize="sm" color="text.muted">
							{lockedReason}
						</Text>
					)}
				</Stack>
				<Flex align="center" gap={3} flexShrink={0}>
					<Text as="span" role="status" fontSize="xs" fontWeight={600} px={2} py={0.5} borderRadius="full" color={mark.color} bg={mark.bg}>
						{mark.label}
					</Text>
					{state === "done" && canChange && (
						<Button variant="link" size="sm" aria-expanded={open} aria-controls={bodyId} onClick={() => setOpen(!open)}>
							{open ? "Close" : "Change"}
						</Button>
					)}
				</Flex>
			</Flex>
			{reduced ? open && body : <Collapse in={open} animateOpacity unmountOnExit>{body}</Collapse>}
		</Box>
	);
}
