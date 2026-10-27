import { Box, Button, Container, Divider, FormControl, FormLabel, Heading, Input, Stack, Text } from "@chakra-ui/react";
import type { GetServerSideProps } from "next";
import Head from "next/head";
import Script from "next/script";
import { useRouter } from "next/router";
import { useCallback, useState } from "react";
import { ErrorBanner } from "@/components/ErrorBanner";
import { OtpInput } from "@/components/OtpInput";
import { useAction } from "@/features/apply/useAction";
import { call } from "@/lib/api";
import { normaliseMobile } from "@/lib/validation";

interface Props {
	googleClientId: string | null;
	otpSignIn: boolean;
}

/** Sign-in options come from the server's environment at request time (one image for every environment). */
export const getServerSideProps: GetServerSideProps<Props> = async () => ({
	props: {
		googleClientId: process.env.ELOKA_STAFF_GOOGLE_CLIENT_ID || null,
		otpSignIn: process.env.ELOKA_STAFF_OTP_SIGN_IN === "true",
	},
});

interface GoogleAccounts {
	accounts: { id: { initialize: (o: object) => void; renderButton: (el: HTMLElement, o: object) => void } };
}

/** Staff sign-in: Google (Workspace accounts with a staff account), or OTP when running locally. */
export default function StaffSignIn({ googleClientId: GOOGLE_CLIENT_ID, otpSignIn: OTP_SIGN_IN }: Props) {
	const router = useRouter();
	const [mobile, setMobile] = useState("");
	const [sent, setSent] = useState(false);
	const [code, setCode] = useState("");
	const { busy, error, setError, run } = useAction();

	const google = useCallback(
		(element: HTMLDivElement | null) => {
			const g = (window as unknown as { google?: GoogleAccounts }).google;
			if (!element || !g || !GOOGLE_CLIENT_ID) {
				return;
			}
			g.accounts.id.initialize({
				client_id: GOOGLE_CLIENT_ID,
				callback: (response: { credential: string }) =>
					run(async () => {
						await call("/v1/admin/auth/google", { body: { id_token: response.credential } });
						await router.push("/staff/queue");
					}),
			});
			g.accounts.id.renderButton(element, { theme: "outline", size: "large", text: "signin_with" });
		},
		[GOOGLE_CLIENT_ID, router, run],
	);
	const [scriptReady, setScriptReady] = useState(false);

	const sendCode = async () => {
		const normalised = normaliseMobile(mobile);
		if (!normalised) {
			setError("Enter a valid 10-digit mobile number");
			return;
		}
		await run(async () => {
			await call("/v1/admin/auth/otp", { body: { mobile: normalised } });
			setSent(true);
		});
	};

	const verify = () =>
		run(async () => {
			await call("/v1/admin/auth/otp/verify", { body: { mobile: normaliseMobile(mobile), otp: code } });
			await router.push("/staff/queue");
		});

	return (
		<>
			<Head>
				<title>Staff sign-in · ELOKA</title>
			</Head>
			{GOOGLE_CLIENT_ID && <Script src="https://accounts.google.com/gsi/client" onLoad={() => setScriptReady(true)} />}
			<Container maxW="440px" py={16}>
				<Stack spacing={6} bg="bg.surface" border="1px solid" borderColor="border.hairline" borderRadius="card" p={8}>
					<Heading as="h1" size="md">
						ELOKA review console
					</Heading>
					<Text color="text.muted">For Eko staff only.</Text>
					{GOOGLE_CLIENT_ID && scriptReady && <Box ref={google} />}
					{!GOOGLE_CLIENT_ID && !OTP_SIGN_IN && <Text>Staff sign-in is not configured.</Text>}
					{OTP_SIGN_IN && (
						<>
							{GOOGLE_CLIENT_ID && <Divider />}
							<FormControl isDisabled={sent}>
								<FormLabel htmlFor="staff-mobile">Mobile number (local sign-in)</FormLabel>
								<Input id="staff-mobile" inputMode="numeric" value={mobile} onChange={(event) => setMobile(event.target.value)} />
							</FormControl>
							{!sent ? (
								<Button onClick={sendCode} isLoading={busy}>
									Send code
								</Button>
							) : (
								<>
									<OtpInput value={code} onChange={setCode} />
									<Button onClick={verify} isLoading={busy}>
										Sign in
									</Button>
								</>
							)}
						</>
					)}
					<ErrorBanner message={error} />
				</Stack>
			</Container>
		</>
	);
}
