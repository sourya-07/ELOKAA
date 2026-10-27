import { Button, FormControl, FormErrorMessage, FormHelperText, FormLabel, Input, Stack } from "@chakra-ui/react";
import { useRouter } from "next/router";
import { useState } from "react";
import { ErrorBanner } from "@/components/ErrorBanner";
import { ERROR_TEXT, routeFor } from "@/lib/steps";
import { isPersonalPan } from "@/lib/validation";
import { useApplication } from "./ApplicationContext";
import { useAction } from "./useAction";

/**
 * PAN check. On the DigiLocker path the name and date of birth come from DigiLocker, so only the
 * PAN is asked for; step by step, the name and date of birth as on the PAN card too.
 */
export function PanForm({ askPerson }: { askPerson: boolean }) {
	const { snapshot, write } = useApplication();
	const router = useRouter();
	const step = snapshot.steps.find((s) => s.step_key === "PAN");
	const [pan, setPan] = useState("");
	const [name, setName] = useState("");
	const [dob, setDob] = useState("");
	const [fieldError, setFieldError] = useState<string | null>(null);
	const { busy, error, run } = useAction();
	const blocked = step?.status === "BLOCKED";
	const stepError = step?.last_error_code && step.status !== "COMPLETED" ? ERROR_TEXT[step.last_error_code] : null;

	const submit = async () => {
		const value = pan.trim().toUpperCase();
		if (!isPersonalPan(value)) {
			setFieldError("Enter your personal PAN, for example ABCPK1234L");
			return;
		}
		if (askPerson && (!name.trim() || !dob)) {
			setFieldError("Enter your name and date of birth as on your PAN card");
			return;
		}
		setFieldError(null);
		await run(async () => {
			const next = await write("/pan-verifications", {
				body: askPerson ? { pan: value, name: name.trim(), dob } : { pan: value },
				idempotent: true,
			});
			const after = next.steps.find((s) => s.step_key === "PAN");
			if (after?.status === "COMPLETED") {
				await router.push(routeFor(next));
			}
		});
	};

	return (
		<Stack spacing={4}>
			<FormControl isInvalid={!!fieldError} isDisabled={blocked}>
				<FormLabel htmlFor="pan">PAN</FormLabel>
				<Input id="pan" autoCapitalize="characters" maxLength={10} value={pan} onChange={(event) => setPan(event.target.value.toUpperCase())} />
				<FormHelperText>Your personal PAN (10 characters).</FormHelperText>
				{!askPerson && <FormErrorMessage>{fieldError}</FormErrorMessage>}
			</FormControl>
			{askPerson && (
				<>
					<FormControl isDisabled={blocked}>
						<FormLabel htmlFor="name">Name as on your PAN card</FormLabel>
						<Input id="name" autoComplete="name" value={name} onChange={(event) => setName(event.target.value)} />
					</FormControl>
					<FormControl isInvalid={!!fieldError} isDisabled={blocked}>
						<FormLabel htmlFor="dob">Date of birth</FormLabel>
						<Input id="dob" type="date" value={dob} onChange={(event) => setDob(event.target.value)} />
						<FormErrorMessage>{fieldError}</FormErrorMessage>
					</FormControl>
				</>
			)}
			<ErrorBanner
				message={blocked ? "Too many failed attempts. Our team will contact you." : stepError ? `${stepError} ${(step?.max_attempts ?? 0) - (step?.attempt_count ?? 0)} attempts left.` : null}
				tone={blocked ? "error" : "warning"}
			/>
			<ErrorBanner message={error} />
			<Button onClick={submit} isLoading={busy} loadingText="Checking" isDisabled={blocked}>
				Verify PAN
			</Button>
		</Stack>
	);
}
