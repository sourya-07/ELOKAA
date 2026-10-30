import { Box, Button, Container, Flex, Heading, HStack, Stack, Text } from "@chakra-ui/react";
import Head from "next/head";
import NextLink from "next/link";
import { useRouter } from "next/router";
import { type ReactNode, useEffect, useRef } from "react";
import { LanguageSelector } from "@/components/LanguageSelector";
import { useApplicationSession } from "@/features/apply/ApplicationContext";
import { useI18n } from "@/i18n/I18nProvider";
import { call } from "@/lib/api";
import { milestonesAtStart, milestonesOf, type MilestoneState } from "@/lib/milestones";
import type { Snapshot } from "@/lib/types";
import { EASE_OUT, prefersReducedMotion, journeyFeedback, staggerChildren, useRevealOnScroll } from "@/styles/motion";

interface Props {
	title: string;
	subtitle?: ReactNode;
	/** The application, for the progress line ("start" before sign-in); leave out on pages outside the journey steps */
	progress?: Snapshot | "start";
	/** Show "Sign out" (defaults to whether progress is shown) */
	signedIn?: boolean;
	/** Shown above the title, e.g. the tick on a good outcome */
	mark?: ReactNode;
	children: ReactNode;
}

/** Page frame of the applicant journey: brand bar, step counter and one focused column. */
export function ApplyShell({ title, subtitle, progress, signedIn = !!progress && progress !== "start", mark, children }: Props) {
	const heading = useRef<HTMLHeadingElement>(null);
	const reveal = useRevealOnScroll<HTMLDivElement>();
	const router = useRouter();
	const { t } = useI18n();
	// Came here from "Fix" or "Change" on the review page: one click back
	const backToReview = router?.query?.return === "review" && router.pathname !== "/apply/review";
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
				<title>{`${title} · ${t("shell.titleSuffix")}`}</title>
			</Head>
			<Box
				as="a"
				href="#main"
				position="absolute"
				left={2}
				top={2}
				zIndex="skipLink"
				bg="bg.surface"
				px={3}
				py={2}
				borderRadius="control"
				transform="translateY(-200%)"
				_focusVisible={{ transform: "none" }}
			>
				{t("shell.skip")}
			</Box>
			<Box as="header" borderBottom="1px solid" borderColor="border.hairline" bg="bg.surface">
				<Container maxW="720px" py={4}>
					<Flex justify="space-between" align="center">
						<Text as={NextLink} href="/apply" fontWeight={700} fontSize="lg" letterSpacing="-0.01em" translate="no">
							ELOKA
						</Text>
						<HStack spacing={4}>
							<LanguageSelector />
							{/* Journey steps only: the status page shows the application and lets the applicant withdraw */}
							{progress && progress !== "start" && (
								<Text as={NextLink} href="/apply/status" fontSize="sm" color="text.link">
									{t("shell.myApplication")}
								</Text>
							)}
							{signedIn && <SignOut />}
						</HStack>
					</Flex>
				</Container>
			</Box>
			<Container as="main" id="main" maxW="720px" py={{ base: 6, md: 10 }} sx={journeyFeedback}>
				{/* A new page (or a new part of one) enters in order instead of appearing all at once; what
				    starts below the screen rises in when scrolled to */}
				<Stack ref={reveal} spacing={6} sx={staggerChildren()}>
					{progress && <Milestones states={progress === "start" ? milestonesAtStart() : milestonesOf(progress)} />}
					{backToReview && (
						<Text as={NextLink} href="/apply/review" fontSize="sm" color="text.link" alignSelf="flex-start">
							{t("shell.backToReview")}
						</Text>
					)}
					{/* Its own box: the page's entrance moves the box, the mark animates inside it */}
					{mark && <Box>{mark}</Box>}
					<Stack spacing={2}>
						<Heading as="h1" size="lg" letterSpacing="-0.01em" ref={heading} tabIndex={-1} _focus={{ outline: "none" }} _focusVisible={{ outline: "2px solid", outlineColor: "connect.500", outlineOffset: "4px" }}>
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
	const { signOut } = useApplicationSession();
	const { t } = useI18n();
	return (
		<Button
			variant="link"
			size="sm"
			color="text.link"
			fontWeight={400}
			onClick={async () => {
				await call("/v1/partner/session", { method: "DELETE" }).catch(() => undefined);
				signOut();
				await router.replace("/apply");
			}}
		>
			{t("shell.signOut")}
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
			{/* The parts by name on wider screens (a phone shows "Now: …" above) */}
			<HStack spacing={1} mt={2} display={{ base: "none", md: "flex" }} aria-hidden>
				{states.map((state) => (
					<Text key={state.key} flex="1" fontSize="xs" color={state.current ? "connect.500" : state.done ? "text.primary" : "text.muted"} fontWeight={state.current ? 600 : 400} noOfLines={1}>
						{state.label}
					</Text>
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
		<Box flex="1" h="6px" bg="paper.300" borderRadius="full" overflow="hidden" title={state.label}>
			{(state.done || state.current) && (
				// done: full colour; now: a lighter shade of it, so the current part stands out from those still to come
				<Box ref={bar} h="100%" bg="connect.500" opacity={state.done ? 1 : 0.35} transformOrigin="left" />
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
