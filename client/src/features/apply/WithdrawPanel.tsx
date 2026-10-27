import { Button, Select, Stack, Text } from "@chakra-ui/react";
import { useState } from "react";
import { OtpInput } from "@/components/OtpInput";
import { ErrorBanner } from "@/components/ErrorBanner";
import { Section } from "@/components/ApplyShell";
import { useApplication } from "@/features/apply/ApplicationContext";
import { useAction } from "@/features/apply/useAction";
import { call } from "@/lib/api";

const REASONS: Record<string, string> = {
	CHANGED_MIND: "I changed my mind",
	TAKES_TOO_LONG: "It takes too long",
	WILL_APPLY_LATER: "I will apply later",
	OTHER: "Another reason",
};

/** Withdrawing for good: the server asks for a fresh code to the applicant's own mobile first. */
export function WithdrawPanel() {
	const { write } = useApplication();
	const { busy, error, run } = useAction();
	const [stage, setStage] = useState<"closed" | "confirm" | "code">("closed");
	const [reason, setReason] = useState("CHANGED_MIND");
	const [code, setCode] = useState("");

	if (stage === "closed") {
		return (
			<Button variant="ghost" alignSelf="flex-start" onClick={() => setStage("confirm")}>
				Withdraw application
			</Button>
		);
	}
	const sendCode = () =>
		run(async () => {
			await call("/v1/partner/reauth", { method: "POST" });
			setCode("");
			setStage("code");
		});
	return (
		<Section>
			<Stack spacing={4}>
				<Text fontWeight={600}>Withdraw your application?</Text>
				<Text color="text.muted">You cannot undo this. To apply again later you will start a new application.</Text>
				<Select aria-label="Why are you withdrawing?" value={reason} onChange={(event) => setReason(event.target.value)}>
					{Object.entries(REASONS).map(([value, label]) => (
						<option key={value} value={value}>
							{label}
						</option>
					))}
				</Select>
				<ErrorBanner message={error} />
				{stage === "confirm" ? (
					<Button onClick={sendCode} isLoading={busy}>
						Send code to my mobile
					</Button>
				) : (
					<>
						<Text>Enter the code we sent to your mobile.</Text>
						<OtpInput value={code} onChange={setCode} isDisabled={busy} />
						<Button
							colorScheme="red"
							isLoading={busy}
							isDisabled={code.length !== 6}
							onClick={() => run(() => write("/withdraw", { body: { otp: code, reason_code: reason } }))}
						>
							Withdraw
						</Button>
						<Button variant="link" alignSelf="flex-start" onClick={sendCode} isDisabled={busy}>
							Send a new code
						</Button>
					</>
				)}
				<Button variant="ghost" alignSelf="flex-start" onClick={() => setStage("closed")} isDisabled={busy}>
					Keep my application
				</Button>
			</Stack>
		</Section>
	);
}
