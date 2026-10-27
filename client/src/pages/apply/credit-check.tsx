import { Button, Checkbox, Spinner, Stack, Text } from "@chakra-ui/react";
import { useRouter } from "next/router";
import { useState } from "react";
import { ApplyShell, Section } from "@/components/ApplyShell";
import { ErrorBanner } from "@/components/ErrorBanner";
import { useApplication, useStepGuard } from "@/features/apply/ApplicationContext";
import { canOpen } from "@/features/apply/guards";
import { useAction } from "@/features/apply/useAction";
import { withApplication } from "@/features/apply/withApplication";
import { CONSENT_VERSION } from "@/lib/consent";
import { ERROR_TEXT, position, routeFor } from "@/lib/steps";

/** The CIBIL check right after the PAN. Only the outcome is shown, never the score. */
function CreditCheckPage() {
	const { snapshot, write } = useApplication();
	const router = useRouter();
	useStepGuard((s) => canOpen(s, "CREDIT_CHECK"));
	const step = snapshot.steps.find((s) => s.step_key === "CREDIT_CHECK");
	const [agreed, setAgreed] = useState(false);
	const { busy, error, setError, run } = useAction();
	const blocked = step?.status === "BLOCKED";

	const check = async () => {
		if (!agreed) {
			setError("Allow the credit check to continue");
			return;
		}
		await run(async () => {
			await write("/consents", { body: { purpose: "CREDIT_CHECK", version: CONSENT_VERSION } });
			const next = await write("/credit-assessments", { idempotent: true });
			if (next.status === "INELIGIBLE" || next.steps.find((s) => s.step_key === "CREDIT_CHECK")?.status === "COMPLETED") {
				await router.push(routeFor(next));
			}
		});
	};

	const failure = step?.last_error_code && step.status !== "COMPLETED" ? ERROR_TEXT[step.last_error_code] : null;
	return (
		<ApplyShell title="Credit check" subtitle="We check your CIBIL credit report to confirm you are eligible." step={position(snapshot.method, "/apply/credit-check")}>
			<Section>
				{busy ? (
					<Stack direction="row" align="center" spacing={3}>
						<Spinner size="sm" />
						<Text>Checking your eligibility…</Text>
					</Stack>
				) : (
					<Checkbox isChecked={agreed} onChange={(event) => setAgreed(event.target.checked)} isDisabled={blocked} alignItems="flex-start">
						I allow ELOKA to get my credit information from a credit bureau for this application.
					</Checkbox>
				)}
				<ErrorBanner
					message={blocked ? "We could not complete your credit check. Our team will contact you." : failure ? `${failure} ${(step?.max_attempts ?? 0) - (step?.attempt_count ?? 0)} attempts left.` : null}
					tone={blocked ? "error" : "warning"}
				/>
				<ErrorBanner message={error} />
				<Button onClick={check} isLoading={busy} isDisabled={blocked}>
					{failure ? "Try again" : "Check eligibility"}
				</Button>
			</Section>
		</ApplyShell>
	);
}

export default withApplication(CreditCheckPage);
