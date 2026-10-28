import { Button, FormControl, FormErrorMessage, FormHelperText, FormLabel, Input, Text } from "@chakra-ui/react";
import { useRouter } from "next/router";
import { useState } from "react";
import { ApplyShell, Section } from "@/components/ApplyShell";
import { ErrorBanner } from "@/components/ErrorBanner";
import { useApplication, useStepGuard } from "@/features/apply/ApplicationContext";
import { canOpen } from "@/features/apply/guards";
import { useAction } from "@/features/apply/useAction";
import { withApplication } from "@/features/apply/withApplication";
import { routeFor} from "@/lib/steps";
import { checkFile, PAPER } from "@/lib/validation";

/** Step by step: masked Aadhaar front and back, and the last 4 digits. Staff check them by hand. */
function AadhaarPage() {
	const { snapshot, write } = useApplication();
	const router = useRouter();
	useStepGuard((s) => canOpen(s, "AADHAAR") && s.method === "MANUAL");
	const [front, setFront] = useState<File | null>(null);
	const [back, setBack] = useState<File | null>(null);
	const [last4, setLast4] = useState("");
	const [fieldError, setFieldError] = useState<string | null>(null);
	const { busy, error, run } = useAction();

	const pick = (setter: (file: File | null) => void) => (event: React.ChangeEvent<HTMLInputElement>) => {
		const file = event.target.files?.[0] ?? null;
		const problem = file ? checkFile(file, PAPER) : null;
		setFieldError(problem);
		setter(problem ? null : file);
	};

	const submit = async () => {
		if (!front || !back) {
			setFieldError("Upload both sides of your Aadhaar card");
			return;
		}
		if (!/^\d{4}$/.test(last4)) {
			setFieldError("Enter the last 4 digits of your Aadhaar number");
			return;
		}
		setFieldError(null);
		const form = new FormData();
		form.append("front", front, front.name);
		form.append("back", back, back.name);
		form.append("aadhaar_last4", last4);
		await run(async () => {
			const next = await write("/aadhaar", { form });
			await router.push(routeFor(next));
		});
	};

	return (
		<ApplyShell title="Aadhaar" subtitle="Upload both sides of your Aadhaar card. Our team checks them by hand." progress={snapshot}>
			<Section>
				<Text fontSize="sm" bg="status.warn.bg" color="status.warn.fg" p={3} borderRadius="control">
					Cover the first 8 digits of the Aadhaar number before uploading. We send back cards that show the full number.
				</Text>
				<FormControl>
					<FormLabel htmlFor="front">Front side</FormLabel>
					<Input id="front" type="file" accept={PAPER.types.join(",")} onChange={pick(setFront)} p={1.5} />
					<FormHelperText>{PAPER.label}</FormHelperText>
				</FormControl>
				<FormControl>
					<FormLabel htmlFor="back">Back side</FormLabel>
					<Input id="back" type="file" accept={PAPER.types.join(",")} onChange={pick(setBack)} p={1.5} />
				</FormControl>
				<FormControl isInvalid={!!fieldError}>
					<FormLabel htmlFor="last4">Last 4 digits of your Aadhaar number</FormLabel>
					<Input id="last4" inputMode="numeric" maxLength={4} value={last4} onChange={(event) => setLast4(event.target.value.replace(/\D/g, ""))} w="120px" />
					<FormErrorMessage>{fieldError}</FormErrorMessage>
				</FormControl>
			</Section>
			<ErrorBanner message={error} />
			<Button onClick={submit} isLoading={busy} loadingText="Uploading">
				Continue
			</Button>
		</ApplyShell>
	);
}

export default withApplication(AadhaarPage);
