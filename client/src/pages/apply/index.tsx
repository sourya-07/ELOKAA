import { Button, Center, FormControl, FormErrorMessage, FormLabel, Input, InputGroup, InputLeftAddon, Spinner, Stack, Text } from "@chakra-ui/react";
import { useRouter } from "next/router";
import { type FormEvent, useEffect, useState } from "react";
import { ApplyShell, Section } from "@/components/ApplyShell";
import { ErrorBanner } from "@/components/ErrorBanner";
import { OtpInput } from "@/components/OtpInput";
import { useApplicationSession, useOptionalApplication } from "@/features/apply/ApplicationContext";
import { EmailStage } from "@/features/apply/EmailStage";
import { useAction } from "@/features/apply/useAction";
import { useCountdown } from "@/features/apply/useCountdown";
import { withOptionalApplication } from "@/features/apply/withApplication";
import { ApiError, call } from "@/lib/api";
import { mobileDigits, normaliseMobile } from "@/lib/validation";
import { waitText } from "@/lib/format";
import { routeFor, safeNext } from "@/lib/steps";
import { canChange } from "@/lib/tasks";
import { delayedAppear } from "@/styles/motion";

interface Sent {
	otp_ref: string;
	expires_in: number;
	resend_after: number;
	otp_length: number;
}

const CONTACT_STEPS = ["EMAIL_VERIFICATION", "CONSENT"];

/**
 * Contact details, on one card: the mobile number (which signs the applicant in), then the email
 * address and the data-use consent. Once those are done the applicant continues where the server says.
 */
function ContactPage() {
	const application = useOptionalApplication();
	const { reload } = useApplicationSession();
	const router = useRouter();
	const snapshot = application?.snapshot;
	const editable = !!snapshot && ["IN_PROGRESS", "CORRECTION_REQUIRED"].includes(snapshot.status);
	// Open while the server's next step is here, or when "Change" on the review page asks for the email
	const changingFromReview = router.query.return === "review" && !!snapshot && canChange(snapshot, "EMAIL_VERIFICATION");
	const contactOpen = !!snapshot && editable && (CONTACT_STEPS.includes(snapshot.next_step ?? "") || changingFromReview);

	useEffect(() => {
		if (snapshot && !contactOpen) {
			router.replace(safeNext(router.query.next) ?? routeFor(snapshot));
		}
	}, [contactOpen, router, snapshot]);

	if (!snapshot) {
		return <MobileStage onSignedIn={reload} />;
	}
	if (!contactOpen) {
		return (
			<Center minH="60vh" sx={delayedAppear}>
				<Spinner label="Opening your application" />
			</Center>
		);
	}
	return (
		<ApplyShell title="Contact details" progress={snapshot}>
			<Section>
				<EmailStage />
			</Section>
		</ApplyShell>
	);
}

export default withOptionalApplication(ContactPage);

/** The mobile number and its one-time code; a correct code signs the applicant in. */
function MobileStage({ onSignedIn }: { onSignedIn: () => void }) {
	const [mobile, setMobile] = useState("");
	const [mobileError, setMobileError] = useState<string | null>(null);
	const [sent, setSent] = useState<Sent | null>(null);
	const [code, setCode] = useState("");
	const { wait, start, fromError } = useCountdown();
	const { busy, error, setError, run } = useAction();

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
			setError("Enter the code we sent you");
			return;
		}
		await run(async () => {
			try {
				await call("/v1/partner/otp/verify", { body: { otp_ref: sent.otp_ref, otp: code } });
				onSignedIn(); // the same card continues with the email once the application is loaded
			} catch (e) {
				if (e instanceof ApiError && (e.code === "OTP_LOCKED" || e.code === "OTP_EXPIRED")) {
					setCode("");
				}
				throw e;
			}
		});
	};

	return (
		<ApplyShell title="Contact details">
			<Section>
				<form onSubmit={send} noValidate>
					<Stack spacing={4}>
						<FormControl isInvalid={!!mobileError} isDisabled={!!sent || busy}>
							<FormLabel htmlFor="mobile">Mobile number</FormLabel>
							<InputGroup>
								<InputLeftAddon>+91</InputLeftAddon>
								<Input
									id="mobile"
									type="tel"
									inputMode="numeric"
									autoComplete="tel-national"
									value={mobile}
									onChange={(event) => setMobile(mobileDigits(event.target.value))}
								/>
							</InputGroup>
							<FormErrorMessage>{mobileError}</FormErrorMessage>
						</FormControl>
						{!sent && (
							<Button type="submit" isLoading={busy} isDisabled={wait > 0}>
								{wait > 0 ? `Try again in ${waitText(wait)}` : "Send code"}
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
							<OtpInput length={sent.otp_length} value={code} onChange={setCode} isInvalid={!!error} isDisabled={busy} />
							<Button type="submit" isLoading={busy}>
								Verify
							</Button>
							<Button variant="link" size="sm" alignSelf="flex-start" isDisabled={wait > 0 || busy} onClick={() => send()}>
								{wait > 0 ? `Resend in ${waitText(wait)}` : "Resend code"}
							</Button>
						</Stack>
					</form>
				)}
				<ErrorBanner message={error} />
			</Section>
		</ApplyShell>
	);
}
