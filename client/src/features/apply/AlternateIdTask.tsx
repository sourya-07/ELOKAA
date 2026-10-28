import { Button, FormControl, FormErrorMessage, FormHelperText, FormLabel, Input, Radio, RadioGroup, Stack, Text } from "@chakra-ui/react";
import { useState } from "react";
import { ErrorBanner } from "@/components/ErrorBanner";
import { ApiError } from "@/lib/api";
import { ERROR_TEXT } from "@/lib/steps";
import { useApplication } from "./ApplicationContext";
import { useAction } from "./useAction";

type IdType = "VOTER_ID" | "DRIVING_LICENCE";

const FORMAT: Record<IdType, { label: string; help: string; pattern: RegExp; max: number; example: string }> = {
	VOTER_ID: { label: "Voter ID (EPIC) number", help: "10 characters, for example ABC1234567.", pattern: /^[A-Z]{3}\d{7}$/, max: 10, example: "ABC1234567" },
	DRIVING_LICENCE: { label: "Driving licence number", help: "As on the licence, for example MH01 20110012345.", pattern: /^[A-Z]{2}\d{13}$/, max: 20, example: "MH0120110012345" },
};

/**
 * The second identity document: a voter ID or a driving licence, checked with its government record
 * and against the name and date of birth from DigiLocker. One is enough; if one fails, try the other.
 */
export function AlternateIdTask() {
	const { snapshot, write } = useApplication();
	const step = snapshot.steps.find((s) => s.step_key === "ALTERNATE_ID");
	const [type, setType] = useState<IdType>("VOTER_ID");
	const [number, setNumber] = useState("");
	const [numberError, setNumberError] = useState<string | null>(null);
	const [exhausted, setExhausted] = useState<IdType[]>([]);
	const { busy, error, run } = useAction();
	const blocked = step?.status === "BLOCKED";
	const failure = step?.last_error_code && step.status !== "COMPLETED" ? ERROR_TEXT[step.last_error_code] ?? "We could not verify this document." : null;

	const verify = async () => {
		const value = number.toUpperCase().replace(/[\s-]/g, "");
		if (!FORMAT[type].pattern.test(value)) {
			setNumberError(`Enter the number exactly as on the card, for example ${FORMAT[type].example}`);
			return;
		}
		setNumberError(null);
		await run(async () => {
			try {
				await write("/alternate-id-verifications", { body: { type, number: value }, idempotent: true });
			} catch (e) {
				if (e instanceof ApiError && e.code === "ID_TYPE_EXHAUSTED") {
					setExhausted((list) => [...list, type]);
					setType(type === "VOTER_ID" ? "DRIVING_LICENCE" : "VOTER_ID");
					setNumber("");
				}
				throw e;
			}
		});
	};

	if (blocked) {
		return <ErrorBanner tone="error" message="Neither document could be verified. Our team will contact you." />;
	}
	return (
		<Stack spacing={4}>
			<Text>One of these is enough. We check it with the government record and against your name and date of birth from DigiLocker.</Text>
			<RadioGroup value={type} onChange={(value) => { setType(value as IdType); setNumber(""); setNumberError(null); }}>
				<Stack direction={{ base: "column", sm: "row" }} spacing={5}>
					<Radio value="VOTER_ID" isDisabled={exhausted.includes("VOTER_ID")}>Voter ID</Radio>
					<Radio value="DRIVING_LICENCE" isDisabled={exhausted.includes("DRIVING_LICENCE")}>Driving licence</Radio>
				</Stack>
			</RadioGroup>
			<FormControl isInvalid={!!numberError}>
				<FormLabel htmlFor="alternate-number">{FORMAT[type].label}</FormLabel>
				<Input id="alternate-number" autoCapitalize="characters" autoComplete="off" spellCheck={false} fontFamily="mono" maxLength={FORMAT[type].max} value={number} onChange={(event) => setNumber(event.target.value.toUpperCase())} />
				<FormHelperText>{FORMAT[type].help}</FormHelperText>
				<FormErrorMessage>{numberError}</FormErrorMessage>
			</FormControl>
			<ErrorBanner message={failure ? `${failure}${step?.last_error_code === "ALTERNATE_ID_UNAVAILABLE" ? "" : " You can try again or use the other document."}` : null} tone="warning" />
			<ErrorBanner message={error} />
			<Button onClick={verify} isLoading={busy} loadingText="Checking" alignSelf="flex-start">
				Verify
			</Button>
		</Stack>
	);
}
