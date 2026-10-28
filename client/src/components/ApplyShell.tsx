import { Box, Container, Flex, Heading, Stack, Text } from "@chakra-ui/react";
import Head from "next/head";
import NextLink from "next/link";
import { type ReactNode, useEffect, useRef } from "react";
import { EASE_IN_OUT, prefersReducedMotion, journeyFeedback, staggerChildren } from "@/styles/motion";

interface Props {
	title: string;
	subtitle?: ReactNode;
	step?: { index: number; total: number };
	/** Shown above the title, e.g. the tick on a good outcome */
	mark?: ReactNode;
	children: ReactNode;
}

/** Page frame of the applicant journey: brand bar, step counter and one focused column. */
export function ApplyShell({ title, subtitle, step, mark, children }: Props) {
	const percent = step && step.total ? Math.round((step.index / step.total) * 100) : undefined;
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
						{/* Journey steps only: the status page shows the application and lets the applicant withdraw */}
						{step && (
							<Text as={NextLink} href="/apply/status" fontSize="sm" color="text.link">
								My application
							</Text>
						)}
					</Flex>
				</Container>
			</Box>
			<Container as="main" maxW="720px" py={{ base: 6, md: 10 }} sx={journeyFeedback}>
				{/* A new page (or a new part of one) enters in order instead of appearing all at once */}
				<Stack spacing={6} sx={staggerChildren()}>
					{step && step.index > 0 && (
						<Box>
							<Flex justify="space-between" fontSize="sm" color="text.muted" mb={2}>
								<Text>
									Step {step.index} of {step.total}
								</Text>
								<Text>{percent}%</Text>
							</Flex>
							<StepLine percent={percent ?? 0} />
						</Box>
					)}
					{/* Its own box: the page's entrance moves the box, the mark animates inside it */}
					{mark && <Box>{mark}</Box>}
					<Stack spacing={2}>
						<Heading as="h1" size="lg" letterSpacing="-0.01em">
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

// The share drawn on the previous page, so the line moves on from there instead of jumping
let drawn: number | null = null;

/** The journey's progress line. Arriving on a step moves it from where the last page left it. */
function StepLine({ percent }: { percent: number }) {
	const bar = useRef<HTMLDivElement>(null);
	const share = percent / 100;
	useEffect(() => {
		const from = drawn;
		drawn = share;
		if (from === null || from === share || !bar.current?.animate || prefersReducedMotion()) {
			return;
		}
		bar.current.animate([{ transform: `scaleX(${from})` }, { transform: `scaleX(${share})` }], {
			duration: 250,
			easing: EASE_IN_OUT,
		});
	}, [share]);
	return (
		<Box role="progressbar" aria-label="Progress" aria-valuemin={0} aria-valuemax={100} aria-valuenow={percent} h="4px" bg="paper.300" borderRadius="full" overflow="hidden">
			<Box ref={bar} h="100%" bg="connect.500" transformOrigin="left" style={{ transform: `scaleX(${share})` }} />
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
