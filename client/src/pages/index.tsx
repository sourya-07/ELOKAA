import { Button, Container, Heading, List, ListItem, Stack, Text } from "@chakra-ui/react";
import Head from "next/head";
import NextLink from "next/link";

/** Start page: what the application asks for, and the way in. */
export default function Home() {
	return (
		<>
			<Head>
				<title>Become an ELOKA partner</title>
			</Head>
			<Container maxW="640px" py={16}>
				<Stack spacing={6}>
					<Heading as="h1" size="xl" letterSpacing="-0.02em">
						Become an ELOKA partner
					</Heading>
					<Text color="text.muted">Apply online in about ten steps. Your progress is saved, so you can come back any time.</Text>
					<List spacing={2} styleType="disc" pl={5}>
						<ListItem>Your mobile number</ListItem>
						<ListItem>DigiLocker, or your Aadhaar card and PAN</ListItem>
						<ListItem>Your graduation certificate</ListItem>
						<ListItem>Your voter ID or driving licence</ListItem>
					</List>
					<Button as={NextLink} href="/apply" alignSelf="flex-start">
						Start your application
					</Button>
				</Stack>
			</Container>
		</>
	);
}
