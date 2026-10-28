import { Button, FormControl, FormErrorMessage, FormHelperText, FormLabel, Input, Stack } from "@chakra-ui/react";
import { useState } from "react";
import { ErrorBanner } from "@/components/ErrorBanner";
import { ERROR_TEXT } from "@/lib/steps";
import { isPersonalPan } from "@/lib/validation";
import { useApplication } from "./ApplicationContext";
import { useAction } from "./useAction";

/**
 * PAN check. The name and date of birth it is checked against come from DigiLocker, so only the
 * PAN is asked for. The page moves on by itself once the server has completed the step.
 */
export function PanForm() {
	const { snapshot, write } = useApplication();
	const step = snapshot.steps.find((s) => s.step_key === "PAN");
	const [pan, setPan] = useState("");
	const [panError, setPanError] = useState<string | null>(null);
	const { busy, error, run } = useAction();
	const blocked = step?.status === "BLOCKED";
	const stepError = step?.last_error_code && step.status !== "COMPLETED" ? ERROR_TEXT[step.last_error_code] : null;

	const submit = async () => {
		const value = pan.trim().toUpperCase();
		if (!isPersonalPan(value)) {
			setPanError("Enter your personal PAN, for example ABCPK1234L");
			return;
		}
		setPanError(null);
		await run(() => write("/pan-verifications", { body: { pan: value }, idempotent: true }));
	};

	return (
		<Stack spacing={4}>
			<FormControl isInvalid={!!panError} isDisabled={blocked}>
				<FormLabel htmlFor="pan">PAN</FormLabel>
				<Input
					id="pan"
					autoCapitalize="characters"
					autoComplete="off"
					fontFamily="mono"
					maxLength={10}
					value={pan}
					onChange={(event) => setPan(event.target.value.toUpperCase())}
				/>
				<FormHelperText>Your personal PAN (10 characters). We check it against the name and date of birth from DigiLocker.</FormHelperText>
				<FormErrorMessage>{panError}</FormErrorMessage>
			</FormControl>
			<ErrorBanner
				message={blocked ? "Too many failed attempts. Our team will contact you." : stepError ? `${stepError} ${(step?.max_attempts ?? 0) - (step?.attempt_count ?? 0)} attempts left.` : null}
				tone={blocked ? "error" : "warning"}
			/>
			<ErrorBanner message={error} />
			<Button onClick={submit} isLoading={busy} loadingText="Checking" isDisabled={blocked} alignSelf="flex-start">
				Verify PAN
			</Button>
		</Stack>
	);
}
