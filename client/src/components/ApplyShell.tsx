import { Box, Button, Container, Flex, Heading, HStack, Stack, Text } from "@chakra-ui/react";
import Head from "next/head";
import NextLink from "next/link";
import { useRouter } from "next/router";
import { AnimatePresence, motion } from "framer-motion";
import { type ReactNode, useEffect, useRef } from "react";
import { LanguageSelector } from "@/components/LanguageSelector";
import { useApplicationSession } from "@/features/apply/ApplicationContext";
import { useI18n } from "@/i18n/I18nProvider";
import { call } from "@/lib/api";
import { milestonesAtStart, milestonesOf, type MilestoneState } from "@/lib/milestones";
import type { Snapshot } from "@/lib/types";
import { EASE_IN_OUT_CURVE, EASE_OUT_CURVE, journeyFeedback, staggerChildren, useRevealOnScroll } from "@/styles/motion";

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
							{/* Journey pages link to the status page ("My application"), which does not link to itself */}
							{progress && progress !== "start" && router?.pathname !== "/apply/status" && (
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

const MotionBox = motion.create(Box);

/**
 * Progress as parts of the application (contact, identity, eligibility, ...), each one finished
 * or not, from the server's step statuses. What moves, and only to explain a change: parts finished
 * since the last page fill in one after another, the part to do now fills in after them each time a
 * page opens, its name is underlined, and "Now: …" and the count slide in when they change. Under
 * reduced motion (MotionConfig in _app) only the fades stay. Nothing loops.
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
	// The newly finished parts fill first, in order; the current part follows them
	const freshOrder = states.filter((state) => fresh.current.has(state.key)).map((state) => state.key);
	const currentDelay = freshOrder.length * 0.08 + 0.1;
	const now = current ? `Now: ${current.label}` : done.length === states.length ? "All parts done" : "";
	return (
		<Box>
			<Flex justify="space-between" fontSize="sm" color="text.muted" mb={2} gap={2} overflow="hidden">
				<Changing id="now" value={now} />
				<Changing id="count" value={`${done.length} of ${states.length} done`} />
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
					<Segment
						key={state.key}
						state={state}
						fill={fresh.current.has(state.key) || state.current}
						delay={state.current ? currentDelay : freshOrder.indexOf(state.key) * 0.08}
					/>
				))}
			</HStack>
			{/* The parts by name on wider screens (a phone shows "Now: …" above) */}
			<HStack spacing={1} mt={2} display={{ base: "none", md: "flex" }} aria-hidden>
				{states.map((state) => (
					<Box key={state.key} flex="1" minW={0}>
						<Text fontSize="xs" color={state.current ? "connect.500" : state.done ? "text.primary" : "text.muted"} fontWeight={state.current ? 600 : 400} noOfLines={1}>
							{state.label}
						</Text>
						{state.current && (
							<MotionBox
								h="2px"
								mt="2px"
								w="24px"
								bg="connect.500"
								borderRadius="full"
								transformOrigin="left"
								initial={{ scaleX: 0, opacity: 0 }}
								animate={{ scaleX: 1, opacity: 1 }}
								transition={{ duration: 0.3, delay: currentDelay + 0.15, ease: EASE_OUT_CURVE }}
							/>
						)}
					</Box>
				))}
			</HStack>
		</Box>
	);
}

// What "Now: …" and the count said on the previous page (each page draws the line anew)
const shown = new Map<string, string>();

/**
 * Text that slides up and fades in when it differs from what the previous page showed (and when it
 * changes on the same page); still on the first page and when nothing changed.
 */
function Changing({ id, value }: { id: string; value: string }) {
	const before = useRef(shown.get(id));
	useEffect(() => {
		shown.set(id, value);
	});
	const changed = before.current !== undefined && before.current !== value;
	return (
		<AnimatePresence mode="popLayout" initial={changed}>
			<MotionBox
				as="span"
				key={value}
				display="block"
				initial={{ y: 8, opacity: 0 }}
				animate={{ y: 0, opacity: 1 }}
				exit={{ y: -8, opacity: 0 }}
				transition={{ duration: 0.2, ease: EASE_OUT_CURVE }}
			>
				{value}
			</MotionBox>
		</AnimatePresence>
	);
}

/** One part of the line: done is full colour, now a lighter shade; a part that changed fills in from the left. */
function Segment({ state, fill, delay }: { state: MilestoneState; fill: boolean; delay: number }) {
	return (
		<Box flex="1" h="6px" bg="paper.300" borderRadius="full" overflow="hidden" title={state.label}>
			{(state.done || state.current) && (
				<MotionBox
					h="100%"
					bg="connect.500"
					transformOrigin="left"
					initial={fill ? { scaleX: 0, opacity: state.done ? 1 : 0.35 } : false}
					animate={{ scaleX: 1, opacity: state.done ? 1 : 0.35 }}
					transition={{ duration: 0.3, delay, ease: EASE_IN_OUT_CURVE }}
				/>
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
