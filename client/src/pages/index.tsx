import { Box, Button, Container, Heading, List, ListItem, OrderedList, Stack, Text } from "@chakra-ui/react";
import Head from "next/head";
import NextLink from "next/link";
import { LanguageSelector } from "@/components/LanguageSelector";
import { useI18n } from "@/i18n/I18nProvider";
import { revealChildren, staggerChildren, useRevealOnScroll } from "@/styles/motion";

/** One part of the application, as the progress line names it (keys into the catalog). */
const PARTS = ["contact", "identity", "eligibility", "photos", "documents", "review"] as const;

const READY = ["home.ready.phone", "home.ready.ids", "home.ready.education", "home.ready.camera"] as const;

/**
 * Start page: who can apply (the credit rule first, before anyone spends time), what the
 * application asks for, and the way in.
 */
export default function Home() {
	// The page's parts, and the items of its two lists, rise in as they are scrolled to
	const page = useRevealOnScroll<HTMLDivElement>();
	const parts = useRevealOnScroll<HTMLOListElement>();
	const ready = useRevealOnScroll<HTMLUListElement>();
	const { t } = useI18n();
	return (
		<>
			<Head>
				<title>{t("home.title")}</title>
			</Head>
			<Container as="main" maxW="640px" py={{ base: 10, md: 16 }}>
				<Stack ref={page} spacing={8} sx={staggerChildren()}>
					<Box alignSelf="flex-end">
						<LanguageSelector />
					</Box>
					<Stack spacing={3}>
						<Heading as="h1" size="xl" letterSpacing="-0.02em">
							{t("home.title")}
						</Heading>
						<Text color="text.muted">{t("home.lead")}</Text>
					</Stack>

					<Box as="section" aria-labelledby="who" bg="bg.surface" border="1px solid" borderColor="border.hairline" borderRadius="card" p={{ base: 4, md: 6 }}>
						<Heading as="h2" size="sm" id="who" mb={3}>
							{t("home.who")}
						</Heading>
						<List spacing={2} styleType="disc" pl={5}>
							<ListItem>{t("home.who.age")}</ListItem>
							<ListItem>
								<Text as="span" fontWeight={600}>
									{t("home.who.credit")}
								</Text>
							</ListItem>
							<ListItem>{t("home.who.documents")}</ListItem>
						</List>
					</Box>

					<Stack as="section" aria-labelledby="how" spacing={3}>
						<Heading as="h2" size="sm" id="how">
							{t("home.how")}
						</Heading>
						<OrderedList ref={parts} spacing={3} pl={5} sx={revealChildren}>
							{PARTS.map((part) => (
								<ListItem key={part}>
									<Text as="span" fontWeight={600}>
										{t(`home.part.${part}.title`)}.
									</Text>{" "}
									<Text as="span" color="text.muted">
										{t(`home.part.${part}.body`)}
									</Text>
								</ListItem>
							))}
						</OrderedList>
					</Stack>

					<Stack as="section" aria-labelledby="ready" spacing={3}>
						<Heading as="h2" size="sm" id="ready">
							{t("home.ready")}
						</Heading>
						<Text fontWeight={600}>{t("home.ready.softCopies")}</Text>
						<List ref={ready} spacing={2} styleType="disc" pl={5} sx={revealChildren}>
							{READY.map((key) => (
								<ListItem key={key}>{t(key)}</ListItem>
							))}
						</List>
						<Text fontSize="sm" color="text.muted">
							{t("home.files")}
						</Text>
					</Stack>

					<Button as={NextLink} href="/apply" alignSelf="flex-start">
						{t("home.start")}
					</Button>
				</Stack>
			</Container>
		</>
	);
}
