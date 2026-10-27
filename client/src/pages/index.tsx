import { Container, Heading, Text } from "@chakra-ui/react";
import Head from "next/head";

/** Placeholder start page until the journey screens are built (docs/PARTNER_JOURNEY.md). */
export default function Home() {
	return (
		<>
			<Head>
				<title>ELOKA partner onboarding</title>
			</Head>
			<Container maxW="640px" py={16}>
				<Heading as="h1" size="lg">
					Become an ELOKA partner
				</Heading>
				<Text mt={4}>The application is being built.</Text>
			</Container>
		</>
	);
}
