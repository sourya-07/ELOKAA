import { Box, Button, Container, Flex, Heading, HStack, Stack, Text } from "@chakra-ui/react";
import Head from "next/head";
import NextLink from "next/link";
import { useRouter } from "next/router";
import { type ReactNode, useEffect, useRef } from "react";
import { call } from "@/lib/api";
import { milestonesOf, type MilestoneState } from "@/lib/milestones";
import type { Snapshot } from "@/lib/types";
import { EASE_OUT, prefersReducedMotion, journeyFeedback, staggerChildren } from "@/styles/motion";

interface Props {
	title: string;
	subtitle?: ReactNode;
	/** The application, for the progress line; leave out on pages outside the journey steps */
	progress?: Snapshot;
	/** Show "Sign out" (defaults to whether progress is shown) */
	signedIn?: boolean;
	/** Shown above the title, e.g. the tick on a good outcome */
	mark?: ReactNode;
	children: ReactNode;
}

/** Page frame of the applicant journey: brand bar, step counter and one focused column. */
export function ApplyShell({ title, subtitle, progress, signedIn = !!progress, mark, children }: Props) {
	const heading = useRef<HTMLHeadingElement>(null);
	useEffect(() => {
		// After moving between pages, start screen readers and keyboards at the new page's title
		if (navigated) {
			heading.current?.focus();
		}
		navigated = true;
	}, [title]);
	return (
		<>
			<Head>
				<title>{`${title} · ELOKA partner`}</title>
			</Head>
			<Box as="header" borderBottom="1px solid" borderColor="border.hairline" bg="bg.surface">
				<Container maxW="720px" py={4}>
					<Flex justify="space-between" align="center">
						<Text as={NextLink} href="/apply" fontWeight={700} fontSize="lg" letterSpacing="-0.01em">
							ELOKA
						</Text>
						<HStack spacing={4}>
							{/* Journey steps only: the status page shows the application and lets the applicant withdraw */}
							{progress && (
								<Text as={NextLink} href="/apply/status" fontSize="sm" color="text.link">
									My application
								</Text>
							)}
							{signedIn && <SignOut />}
						</HStack>
					</Flex>
				</Container>
			</Box>
			<Container as="main" maxW="720px" py={{ base: 6, md: 10 }} sx={journeyFeedback}>
				{/* A new page (or a new part of one) enters in order instead of appearing all at once */}
				<Stack spacing={6} sx={staggerChildren()}>
					{progress && <Milestones states={milestonesOf(progress)} />}
					{/* Its own box: the page's entrance moves the box, the mark animates inside it */}
					{mark && <Box>{mark}</Box>}
					<Stack spacing={2}>
						<Heading as="h1" size="lg" letterSpacing="-0.01em" ref={heading} tabIndex={-1} _focus={{ outline: "none" }}>
							{title}
						</Heading>
						{subtitle && <Text color="text.muted">{subtitle}</Text>}
					</Stack>
					{children}
				</Stack>
			</Container>
		</>
	);
}

/** Ends the session; leaving the journey pages also drops the loaded application in the browser. */
function SignOut() {
	const router = useRouter();
	return (
		<Button
			variant="link"
			size="sm"
			color="text.link"
			fontWeight={400}
			onClick={async () => {
				await call("/v1/partner/session", { method: "DELETE" }).catch(() => undefined);
				await router.replace("/apply");
			}}
		>
			Sign out
		</Button>
	);
}

// Set after the first page: only later pages move focus to their title (not the first load)
let navigated = false;

// Parts already drawn as done on the previous page, so only a newly finished part fills in
let drawn: Set<string> | null = null;

/**
 * Progress as parts of the application (contact, identity, eligibility, ...), each one finished
 * or not, from the server's step statuses. A part finished since the last page fills in once.
 */
function Milestones({ states }: { states: MilestoneState[] }) {
	const done = states.filter((state) => state.done);
	const current = states.find((state) => state.current);
	const fresh = useRef<Set<string>>(new Set());
	if (drawn !== null) {
		fresh.current = new Set(done.filter((state) => !drawn?.has(state.key)).map((state) => state.key));
	}
	useEffect(() => {
		drawn = new Set(done.map((state) => state.key));
	});
	return (
		<Box>
			<Flex justify="space-between" fontSize="sm" color="text.muted" mb={2} gap={2}>
				<Text>{current ? `Now: ${current.label}` : done.length === states.length ? "All parts done" : ""}</Text>
				<Text>
					{done.length} of {states.length} done
				</Text>
			</Flex>
			<HStack
				spacing={1}
				role="progressbar"
				aria-label="Progress"
				aria-valuemin={0}
				aria-valuemax={states.length}
				aria-valuenow={done.length}
				aria-valuetext={`${done.length} of ${states.length} parts done${current ? `, now ${current.label}` : ""}`}
			>
				{states.map((state) => (
					<Segment key={state.key} state={state} fill={fresh.current.has(state.key)} />
				))}
			</HStack>
		</Box>
	);
}

function Segment({ state, fill }: { state: MilestoneState; fill: boolean }) {
	const bar = useRef<HTMLDivElement>(null);
	useEffect(() => {
		if (fill && bar.current?.animate && !prefersReducedMotion()) {
			bar.current.animate([{ transform: "scaleX(0)" }, { transform: "scaleX(1)" }], { duration: 300, easing: EASE_OUT });
		}
	}, [fill]);
	return (
		<Box flex="1" h="4px" bg="paper.300" borderRadius="full" overflow="hidden" title={state.label}>
			{(state.done || state.current) && (
				<Box ref={bar} h="100%" bg={state.done ? "connect.500" : "connect.50"} transformOrigin="left" />
			)}
		</Box>
	);
}

/** A white card with a hairline border, for one group of fields. */
export function Section({ children, label }: { children: ReactNode; label?: string }) {
	return (
		<Box as="section" bg="bg.surface" border="1px solid" borderColor="border.hairline" borderRadius="card" p={{ base: 4, md: 6 }}>
			{label && (
				<Heading as="h2" size="sm" mb={4}>
					{label}
				</Heading>
			)}
			<Stack spacing={4}>{children}</Stack>
		</Box>
	);
}
