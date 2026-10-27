import { Button, FormControl, FormErrorMessage, FormLabel, Input, InputGroup, InputLeftAddon, Stack, Text } from "@chakra-ui/react";
import { useRouter } from "next/router";
import { type FormEvent, useEffect, useState } from "react";
import { ApplyShell, Section } from "@/components/ApplyShell";
import { ErrorBanner } from "@/components/ErrorBanner";
import { OtpInput } from "@/components/OtpInput";
import { useAction } from "@/features/apply/useAction";
import { ApiError, call } from "@/lib/api";
import { normaliseMobile } from "@/lib/validation";

interface Sent {
	otp_ref: string;
	expires_in: number;
	resend_after: number;
	otp_length: number;
}

/** Page 1: mobile number and the one-time code. */
export default function MobilePage() {
	const router = useRouter();
	const [mobile, setMobile] = useState("");
	const [mobileError, setMobileError] = useState<string | null>(null);
	const [sent, setSent] = useState<Sent | null>(null);
	const [code, setCode] = useState("");
	const [wait, setWait] = useState(0);
	const { busy, error, setError, run } = useAction();

	useEffect(() => {
		if (wait <= 0) {
			return;
		}
		const timer = setTimeout(() => setWait(wait - 1), 1000);
		return () => clearTimeout(timer);
	}, [wait]);

	const send = async (event?: FormEvent) => {
		event?.preventDefault();
		const normalised = normaliseMobile(mobile);
		if (!normalised) {
			setMobileError("Enter a valid 10-digit mobile number");
			return;
		}
		setMobileError(null);
		await run(async () => {
			try {
				const reply = await call<Sent>("/v1/partner/otp", { body: { mobile: normalised } });
				setSent(reply.data);
				setCode("");
				setWait(Math.max(reply.data.resend_after, 30));
			} catch (e) {
				if (e instanceof ApiError && e.code === "OTP_LIMIT_REACHED" && e.problem.retry_after) {
					setWait(Number(e.problem.retry_after));
				}
				throw e;
			}
		});
	};

	const verify = async (event: FormEvent) => {
		event.preventDefault();
		if (!sent || code.length !== sent.otp_length) {
			setError("Enter the code we sent you");
			return;
		}
		await run(async () => {
			try {
				await call("/v1/partner/otp/verify", { body: { otp_ref: sent.otp_ref, otp: code } });
				await router.push("/apply/continue");
			} catch (e) {
				if (e instanceof ApiError && e.code === "OTP_INVALID") {
					throw new ApiError({ ...e.problem, detail: `The code is not correct. ${e.problem.attempts_left} attempts left.` });
				}
				if (e instanceof ApiError && (e.code === "OTP_LOCKED" || e.code === "OTP_EXPIRED")) {
					setCode("");
				}
				throw e;
			}
		});
	};

	return (
		<ApplyShell title="Your mobile number" subtitle="We use it to sign you in and to contact you about your application.">
			<Section>
				<form onSubmit={send} noValidate>
					<Stack spacing={4}>
						<FormControl isInvalid={!!mobileError} isDisabled={!!sent}>
							<FormLabel htmlFor="mobile">Mobile number</FormLabel>
							<InputGroup>
								<InputLeftAddon>+91</InputLeftAddon>
								<Input
									id="mobile"
									inputMode="numeric"
									autoComplete="tel-national"
									value={mobile}
									onChange={(event) => setMobile(event.target.value)}
								/>
							</InputGroup>
							<FormErrorMessage>{mobileError}</FormErrorMessage>
						</FormControl>
						{!sent && (
							<Button type="submit" isLoading={busy} isDisabled={wait > 0}>
								{wait > 0 ? `Try again in ${wait}s` : "Send code"}
							</Button>
						)}
					</Stack>
				</form>
				{sent && (
					<form onSubmit={verify} noValidate>
						<Stack spacing={4}>
							<Text>
								Enter the {sent.otp_length}-digit code sent to +91 ******{normaliseMobile(mobile)?.slice(6)}.{" "}
								<Button variant="link" size="sm" onClick={() => setSent(null)}>
									Change number
								</Button>
							</Text>
							<OtpInput length={sent.otp_length} value={code} onChange={setCode} isInvalid={!!error} />
							<Button type="submit" isLoading={busy}>
								Verify
							</Button>
							<Button variant="link" size="sm" alignSelf="flex-start" isDisabled={wait > 0 || busy} onClick={() => send()}>
								{wait > 0 ? `Resend code in 0:${String(wait).padStart(2, "0")}` : "Resend code"}
							</Button>
						</Stack>
					</form>
				)}
				<ErrorBanner message={error} />
			</Section>
		</ApplyShell>
	);
}
