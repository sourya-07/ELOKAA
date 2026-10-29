import { Button, Checkbox, Flex, FormControl, FormErrorMessage, FormLabel, Input, Stack, Text } from "@chakra-ui/react";
import { useRouter } from "next/router";
import { type FormEvent, useState } from "react";
import { ErrorBanner } from "@/components/ErrorBanner";
import { OtpInput } from "@/components/OtpInput";
import { ApiError, call } from "@/lib/api";
import { CONSENT_VERSION } from "@/lib/consent";
import { waitText } from "@/lib/format";
import { routeFor } from "@/lib/steps";
import { isDone } from "@/lib/types";
import { normaliseEmail } from "@/lib/validation";
import { useApplication } from "./ApplicationContext";
import { useAction } from "./useAction";
import { useCountdown } from "./useCountdown";

interface Sent {
	challenge_ref: string;
	expires_in: number;
	resend_after: number;
	otp_length: number;
	email_masked: string;
}

/**
 * Second part of contact details, on the sign-in card: the email address (proved with a code),
 * then the data-use consent. What the email is for is said before it is asked for.
 */
export function EmailStage() {
	const { snapshot, write } = useApplication();
	const router = useRouter();
	const step = snapshot.steps.find((s) => s.step_key === "EMAIL_VERIFICATION");
	const verified = isDone(step);
	const verifiedMasked = (step?.summary?.email_masked as string | undefined) ?? null;
	const consentDone = isDone(snapshot.steps.find((s) => s.step_key === "CONSENT"));
	const [changing, setChanging] = useState(!verified);
	const [email, setEmail] = useState("");
	const [emailError, setEmailError] = useState<string | null>(null);
	const [sent, setSent] = useState<Sent | null>(null);
	const [code, setCode] = useState("");
	const { wait, start, fromError } = useCountdown();
	const [agreed, setAgreed] = useState(consentDone);
	const { busy, error, setError, run } = useAction();

	const send = async (event?: FormEvent) => {
		event?.preventDefault();
		const value = normaliseEmail(email);
		if (!value) {
			setEmailError("Enter a valid email address, for example name@example.com");
			return;
		}
		setEmailError(null);
		await run(async () => {
			try {
				const reply = await call<Sent>(`/v1/partner/applications/${snapshot.application_id}/email-verifications`, {
					body: { email: value },
				});
				setSent(reply.data);
				setCode("");
				start(reply.data.resend_after);
			} catch (e) {
				fromError(e);
				throw e;
			}
		});
	};

	const verify = async (event: FormEvent) => {
		event.preventDefault();
		if (!sent || code.length !== sent.otp_length) {
			setError("Enter the code we emailed you");
			return;
		}
		await run(async () => {
			try {
				await write("/email-verifications/verify", { body: { challenge_ref: sent.challenge_ref, otp: code }, idempotent: true });
				setSent(null);
				setChanging(false);
			} catch (e) {
				if (e instanceof ApiError && (e.code === "OTP_LOCKED" || e.code === "OTP_EXPIRED")) {
					setCode("");
				}
				throw e;
			}
		});
	};

	const next = async () => {
		if (!agreed) {
			setError("Agree to how we use your data to continue");
			return;
		}
		await run(async () => {
			const latest = consentDone ? snapshot : await write("/consents", { body: { purpose: "DATA_PROCESSING", version: CONSENT_VERSION } });
			await router.push(router.query.return === "review" ? "/apply/review" : routeFor(latest));
		});
	};

	return (
		<Stack spacing={5}>
			<Flex align="center" gap={2}>
				<Text as="span" aria-hidden color="status.ok.fg" fontWeight={700}>
					✓
				</Text>
				<Text>Mobile number verified</Text>
			</Flex>

			{!changing && verified ? (
				<Flex align="center" gap={2} wrap="wrap">
					<Text as="span" aria-hidden color="status.ok.fg" fontWeight={700}>
						✓
					</Text>
					<Text>
						Email verified: <Text as="span" fontFamily="mono">{verifiedMasked}</Text>
					</Text>
					<Button variant="link" size="sm" onClick={() => setChanging(true)} isDisabled={step?.status === "LOCKED"}>
						Change email
					</Button>
				</Flex>
			) : (
				<Stack spacing={4}>
					<form onSubmit={send} noValidate>
						<Stack spacing={4}>
							<FormControl isInvalid={!!emailError} isDisabled={!!sent}>
								<FormLabel htmlFor="email">Email address</FormLabel>
								<Input id="email" type="email" inputMode="email" autoComplete="email" spellCheck={false} value={email} onChange={(event) => setEmail(event.target.value)} />
								<FormErrorMessage>{emailError}</FormErrorMessage>
							</FormControl>
							{!sent && (
								<Button type="submit" isLoading={busy} isDisabled={wait > 0}>
									{wait > 0 ? `Try again in ${waitText(wait)}` : "Email me a code"}
								</Button>
							)}
						</Stack>
					</form>
					{sent && (
						<form onSubmit={verify} noValidate>
							<Stack spacing={4}>
								<Text>
									Enter the {sent.otp_length}-digit code sent to <Text as="span" fontFamily="mono">{sent.email_masked}</Text>. It can take a minute
									to arrive; check your spam folder too.{" "}
									<Button variant="link" size="sm" onClick={() => setSent(null)}>
										Change email
									</Button>
								</Text>
								<OtpInput length={sent.otp_length} value={code} onChange={setCode} isInvalid={!!error} />
								<Button type="submit" isLoading={busy}>
									Verify email
								</Button>
								<Button variant="link" size="sm" alignSelf="flex-start" isDisabled={wait > 0 || busy} onClick={() => send()}>
									{wait > 0 ? `Resend in ${waitText(wait)}` : "Resend code"}
								</Button>
							</Stack>
						</form>
					)}
				</Stack>
			)}

			{verified && !changing && (
				<Stack spacing={4}>
					<Checkbox isChecked={agreed} isDisabled={consentDone} onChange={(event) => setAgreed(event.target.checked)} alignItems="flex-start">
						I agree that ELOKA uses the details I give here to check my identity and my application, as described in the privacy notice.
					</Checkbox>
					<Button onClick={next} isLoading={busy}>
						Continue
					</Button>
				</Stack>
			)}
			<ErrorBanner message={error} />
		</Stack>
	);
}
