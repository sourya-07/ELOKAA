import { Box, Container, Flex, Heading, Progress, Stack, Text } from "@chakra-ui/react";
import Head from "next/head";
import NextLink from "next/link";
import type { ReactNode } from "react";

interface Props {
	title: string;
	subtitle?: ReactNode;
	step?: { index: number; total: number };
	children: ReactNode;
}

/** Page frame of the applicant journey: brand bar, step counter and one focused column. */
export function ApplyShell({ title, subtitle, step, children }: Props) {
	const percent = step && step.total ? Math.round((step.index / step.total) * 100) : undefined;
	return (
		<>
			<Head>
				<title>{`${title} · ELOKA partner`}</title>
			</Head>
			<Box as="header" borderBottom="1px solid" borderColor="border.hairline" bg="bg.surface">
				<Container maxW="720px" py={4}>
					<Text as={NextLink} href="/apply" fontWeight={700} fontSize="lg" letterSpacing="-0.01em">
						ELOKA
					</Text>
				</Container>
			</Box>
			<Container as="main" maxW="720px" py={{ base: 6, md: 10 }}>
				<Stack spacing={6}>
					{step && step.index > 0 && (
						<Box>
							<Flex justify="space-between" fontSize="sm" color="text.muted" mb={2}>
								<Text>
									Step {step.index} of {step.total}
								</Text>
								<Text>{percent}%</Text>
							</Flex>
							<Progress value={percent} size="xs" borderRadius="full" colorScheme="blackAlpha" aria-label="Progress" />
						</Box>
					)}
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
