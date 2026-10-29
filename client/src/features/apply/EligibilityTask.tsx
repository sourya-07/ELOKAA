import { Button, Checkbox, Spinner, Stack, Text } from "@chakra-ui/react";
import { useRouter } from "next/router";
import { useState } from "react";
import { ErrorBanner } from "@/components/ErrorBanner";
import { ApiError } from "@/lib/api";
import { CONSENT_VERSION } from "@/lib/consent";
import { ERROR_TEXT, routeFor } from "@/lib/steps";
import { delayedAppear } from "@/styles/motion";
import { useApplication } from "./ApplicationContext";
import { useAction } from "./useAction";

/**
 * The CIBIL check, right after the PAN (ADR-0005). The applicant learns only the outcome, never the
 * score. "Could not check" and "not available yet" are told apart from a real "not eligible".
 */
export function EligibilityTask() {
	const { snapshot, write } = useApplication();
	const router = useRouter();
	const step = snapshot.steps.find((s) => s.step_key === "CREDIT_CHECK");
	const [agreed, setAgreed] = useState(false);
	const [unavailable, setUnavailable] = useState(false);
	const { busy, error, setError, run } = useAction();
	const blocked = step?.status === "BLOCKED";

	const check = async () => {
		if (!agreed) {
			setError("Allow the credit check to continue");
			return;
		}
		await run(async () => {
			try {
				await write("/consents", { body: { purpose: "CREDIT_CHECK", version: CONSENT_VERSION } });
				const next = await write("/credit-assessments", { idempotent: true });
				if (next.status === "INELIGIBLE") {
					await router.push(routeFor(next));
				}
			} catch (e) {
				if (e instanceof ApiError && e.code === "PROVIDER_NOT_CONFIGURED") {
					setUnavailable(true);
					return;
				}
				throw e;
			}
		});
	};

	const failure =
		step?.last_error_code && step.status !== "COMPLETED" ? ERROR_TEXT[step.last_error_code] ?? "We could not complete the credit check. Please try again later." : null;
	const attemptsLeft = (step?.max_attempts ?? 0) - (step?.attempt_count ?? 0);
	return (
		<Stack spacing={4}>
			<Text>
				Partners need a CIBIL credit score of 700 or more. With your consent we ask a credit bureau for your score. You will see only whether it meets 700; we never keep your credit report.
			</Text>
			{busy ? (
				<Stack direction="row" align="center" spacing={3} role="status" aria-live="polite" sx={delayedAppear}>
					<Spinner size="sm" />
					<Text>Checking your eligibility…</Text>
				</Stack>
			) : (
				<Checkbox isChecked={agreed} onChange={(event) => setAgreed(event.target.checked)} isDisabled={blocked || unavailable} alignItems="flex-start">
					I allow ELOKA to get my credit information from a credit bureau for this application.
				</Checkbox>
			)}
			<ErrorBanner
				message={
					unavailable
						? "Credit checks are not available yet. Your progress is saved, and our team will contact you when you can continue."
						: blocked
							? "We could not complete your credit check. Our team will contact you."
							: failure
								? `${failure} ${attemptsLeft} attempts left.`
								: null
				}
				tone={blocked ? "error" : "warning"}
			/>
			<ErrorBanner message={error} />
			<Button onClick={check} isLoading={busy} isDisabled={blocked || unavailable} alignSelf="flex-start">
				{failure ? "Try again" : "Check eligibility"}
			</Button>
		</Stack>
	);
}
