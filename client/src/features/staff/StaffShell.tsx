import { Box, Button, Container, Flex, Heading, Text } from "@chakra-ui/react";
import Head from "next/head";
import NextLink from "next/link";
import { useRouter } from "next/router";
import { type ReactNode, useEffect, useState } from "react";
import { ApiError, call } from "@/lib/api";
import type { StaffView } from "./types";

/** Frame of the staff console. Loads who is signed in; signed-out staff go to the sign-in page. */
export function StaffShell({ title, children }: { title: string; children: (me: StaffView) => ReactNode }) {
	const router = useRouter();
	const [me, setMe] = useState<StaffView | null>(null);
	useEffect(() => {
		call<StaffView>("/v1/admin/me")
			.then((reply) => setMe(reply.data))
			.catch((error) => {
				if (error instanceof ApiError && (error.status === 401 || error.status === 403)) {
					router.replace("/staff");
				}
			});
	}, [router]);
	const signOut = async () => {
		await call("/v1/admin/auth/session", { method: "DELETE" }).catch(() => undefined);
		await router.replace("/staff");
	};
	return (
		<>
			<Head>
				<title>{`${title} · ELOKA staff`}</title>
			</Head>
			<Box as="header" bg="bg.surface" borderBottom="1px solid" borderColor="border.hairline">
				<Container maxW="1200px" py={3}>
					<Flex justify="space-between" align="center" gap={4} wrap="wrap">
						<Text as={NextLink} href="/staff/queue" fontWeight={700}>
							ELOKA review
						</Text>
						{me && (
							<Flex gap={3} align="center">
								<Text fontSize="sm" color="text.muted">
									{me.display_name} · {me.roles.map((r) => r.toLowerCase()).join(", ")}
								</Text>
								<Button size="sm" variant="outline" onClick={signOut}>
									Sign out
								</Button>
							</Flex>
						)}
					</Flex>
				</Container>
			</Box>
			{me?.test_providers && (
				<Box role="note" bg="status.warn.bg" color="status.warn.fg" borderBottom="1px solid" borderColor="border.hairline">
					<Container maxW="1200px" py={2} fontSize="sm" fontWeight={600}>
						Test environment ({me.environment}): identity, credit and document checks here come from test providers, not real
						records.
					</Container>
				</Box>
			)}
			<Container as="main" maxW="1200px" py={8}>
				<Heading as="h1" size="lg" mb={6}>
					{title}
				</Heading>
				{me && children(me)}
			</Container>
		</>
	);
}
