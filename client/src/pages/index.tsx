import { Box, Button, Container, Heading, List, ListItem, OrderedList, Stack, Text } from "@chakra-ui/react";
import Head from "next/head";
import NextLink from "next/link";
import { staggerChildren } from "@/styles/motion";

/** One part of the application, as the progress line names it. */
const PARTS = [
	{ title: "Contact details", body: "Verify your mobile number and your email address with one-time codes." },
	{ title: "Identity", body: "Your Aadhaar through DigiLocker (it gives your home address too), your PAN, and your voter ID or driving licence number." },
	{
		title: "Eligibility",
		body: "With your consent we check your CIBIL credit score. We tell you only whether it is 700 or more, and we never keep your credit report.",
	},
	{ title: "Photos", body: "A live photo taken with your camera (we record where it is taken, so you will be asked to share your location), and a passport-size photo." },
	{
		title: "Documents",
		body: "Your Aadhaar (front and back), PAN card, voter ID or driving licence, class 10 and 12 marksheets, and your graduation or diploma certificate.",
	},
	{ title: "Review and submit", body: "Check everything, then submit. Two people in our team review it." },
];

/**
 * Start page: who can apply (the credit rule first, before anyone spends time), what the
 * application asks for, and the way in.
 */
export default function Home() {
	return (
		<>
			<Head>
				<title>Become an ELOKA partner</title>
			</Head>
			<Container as="main" maxW="640px" py={{ base: 10, md: 16 }}>
				<Stack spacing={8} sx={staggerChildren()}>
					<Stack spacing={3}>
						<Heading as="h1" size="xl" letterSpacing="-0.02em">
							Become an ELOKA partner
						</Heading>
						<Text color="text.muted">
							Apply online in about 20 minutes. Your progress is saved, so you can stop and come back any time.
						</Text>
					</Stack>

					<Box as="section" aria-labelledby="who" bg="bg.surface" border="1px solid" borderColor="border.hairline" borderRadius="card" p={{ base: 4, md: 6 }}>
						<Heading as="h2" size="sm" id="who" mb={3}>
							Who can apply
						</Heading>
						<List spacing={2} styleType="disc" pl={5}>
							<ListItem>You are 18 or older and live in India.</ListItem>
							<ListItem>
								<Text as="span" fontWeight={600}>
									Your CIBIL credit score is 700 or more.
								</Text>{" "}
								With a score below 700 you are not eligible under our current rules, and the application ends at that check.
							</ListItem>
							<ListItem>You have your own Aadhaar, PAN and mobile number, and a DigiLocker account with your Aadhaar in it (you can create one while applying).</ListItem>
						</List>
					</Box>

					<Stack as="section" aria-labelledby="how" spacing={3}>
						<Heading as="h2" size="sm" id="how">
							How it works
						</Heading>
						<OrderedList spacing={3} pl={5}>
							{PARTS.map((part) => (
								<ListItem key={part.title}>
									<Text as="span" fontWeight={600}>
										{part.title}.
									</Text>{" "}
									<Text as="span" color="text.muted">
										{part.body}
									</Text>
								</ListItem>
							))}
						</OrderedList>
					</Stack>

					<Stack as="section" aria-labelledby="ready" spacing={3}>
						<Heading as="h2" size="sm" id="ready">
							Keep these ready
						</Heading>
						<List spacing={2} styleType="disc" pl={5}>
							<ListItem>Your phone, with the mobile number you will use, and your email</ListItem>
							<ListItem>The mobile number linked to your Aadhaar (for DigiLocker), and your PAN</ListItem>
							<ListItem>Your Aadhaar card, voter ID or driving licence, to photograph or scan both sides</ListItem>
							<ListItem>Your class 10 and class 12 marksheets, and your graduation or diploma certificate</ListItem>
							<ListItem>A camera on your phone or computer, in good light</ListItem>
						</List>
						<Text fontSize="sm" color="text.muted">
							Photos and documents: JPG photos up to 5 MB; certificates and cards as JPG or PDF up to 10 MB.
						</Text>
					</Stack>

					<Button as={NextLink} href="/apply" alignSelf="flex-start">
						Start your application
					</Button>
				</Stack>
			</Container>
		</>
	);
}
