import { Box, Button, Checkbox, Heading, List, ListItem, SimpleGrid, Stack, Tag, Text } from "@chakra-ui/react";
import { useRouter } from "next/router";
import { useState } from "react";
import { ApplyShell, Section } from "@/components/ApplyShell";
import { ErrorBanner } from "@/components/ErrorBanner";
import { useApplication, useStepGuard } from "@/features/apply/ApplicationContext";
import { canOpen } from "@/features/apply/guards";
import { useAction } from "@/features/apply/useAction";
import { withApplication } from "@/features/apply/withApplication";
import { CONSENT_VERSION } from "@/lib/consent";
import { routeFor } from "@/lib/steps";
import { isDone } from "@/lib/types";

type Method = "DIGILOCKER" | "MANUAL";

const OPTIONS: { value: Method; title: string; text: string; steps: number; fast?: boolean }[] = [
	{ value: "DIGILOCKER", title: "Apply with DigiLocker", text: "We fetch your name, home address and the last 4 digits of your Aadhaar from DigiLocker.", steps: 8, fast: true },
	{ value: "MANUAL", title: "Apply step by step", text: "Enter your details and upload your Aadhaar yourself. Our team checks it.", steps: 10 },
];

/** Page 2: how to apply, and agreeing to how ELOKA uses the data. */
function MethodPage() {
	const { snapshot, write } = useApplication();
	const router = useRouter();
	useStepGuard((s) => canOpen(s, "VERIFICATION_METHOD") || canOpen(s, "CONSENT"));
	const consentDone = isDone(snapshot.steps.find((s) => s.step_key === "CONSENT"));
	const [method, setMethod] = useState<Method | null>(snapshot.method);
	const [agreed, setAgreed] = useState(consentDone);
	const { busy, error, setError, run } = useAction();

	const next = async () => {
		if (!method) {
			setError("Choose how you want to apply");
			return;
		}
		if (!agreed) {
			setError("Agree to how we use your data to continue");
			return;
		}
		await run(async () => {
			let latest = snapshot;
			if (!consentDone) {
				latest = await write("/consents", { body: { purpose: "DATA_PROCESSING", version: CONSENT_VERSION } });
			}
			if (latest.method !== method || !isDone(latest.steps.find((s) => s.step_key === "VERIFICATION_METHOD"))) {
				latest = await write("/method", { method: "PUT", body: { method } });
			}
			await router.push(routeFor(latest));
		});
	};

	return (
		<ApplyShell title="How would you like to apply?" subtitle="Your progress is saved, so you can come back any time." progress={snapshot}>
			<SimpleGrid columns={{ base: 1, md: 2 }} spacing={4} role="radiogroup" aria-label="How to apply">
				{OPTIONS.map((option) => (
					<Box
						key={option.value}
						as="button"
						type="button"
						role="radio"
						aria-checked={method === option.value}
						onClick={() => setMethod(option.value)}
						textAlign="left"
						bg="bg.surface"
						border="2px solid"
						borderColor={method === option.value ? "ink.900" : "border.hairline"}
						borderRadius="card"
						p={5}
					>
						<Stack spacing={2}>
							<Stack direction="row" justify="space-between" align="center">
								<Heading as="h2" size="sm">
									{option.title}
								</Heading>
								{option.fast && (
									<Tag size="sm" borderRadius="full" bg="connect.50" color="connect.500">
										Faster
									</Tag>
								)}
							</Stack>
							<Text color="text.muted" fontSize="sm">
								{option.text}
							</Text>
							<Text fontSize="sm" fontWeight={600}>
								{option.steps} steps
							</Text>
						</Stack>
					</Box>
				))}
			</SimpleGrid>
			<Section label="Keep these ready">
				<List spacing={2}>
					{["Your Aadhaar-linked mobile (for DigiLocker)", "Your PAN card", "Your graduation certificate", "Your voter ID or driving licence"].map((item) => (
						<ListItem key={item} display="flex" gap={2}>
							<Text as="span" color="status.ok.fg" aria-hidden>
								✓
							</Text>
							{item}
						</ListItem>
					))}
				</List>
			</Section>
			<Checkbox isChecked={agreed} isDisabled={consentDone} onChange={(event) => setAgreed(event.target.checked)} alignItems="flex-start">
				I agree that ELOKA uses the details I give here to check my identity and my application, as described in the privacy notice.
			</Checkbox>
			<ErrorBanner message={error} />
			<Button onClick={next} isLoading={busy}>
				Continue
			</Button>
		</ApplyShell>
	);
}

export default withApplication(MethodPage);
