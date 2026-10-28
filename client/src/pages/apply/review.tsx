import { Button, Checkbox, Flex, Link, Stack, Text } from "@chakra-ui/react";
import NextLink from "next/link";
import { useRouter } from "next/router";
import { useState } from "react";
import { ApplyShell, Section } from "@/components/ApplyShell";
import { ErrorBanner } from "@/components/ErrorBanner";
import { StatusPill } from "@/components/StatusPill";
import { useApplication, useStepGuard } from "@/features/apply/ApplicationContext";
import { useAction } from "@/features/apply/useAction";
import { withApplication } from "@/features/apply/withApplication";
import { DECLARATION_VERSION } from "@/lib/consent";
import { pagesFor} from "@/lib/steps";
import { isDone } from "@/lib/types";

const SUMMARY: Record<string, (s: Record<string, unknown>) => string> = {
	AADHAAR: (s) => `Aadhaar ${s.aadhaar_masked ?? ""}`,
	PAN: (s) => `PAN ${s.pan_masked ?? ""}`,
	CREDIT_CHECK: () => "Eligible",
	HOME_ADDRESS: (s) => `${s.state ?? ""} ${s.pincode ?? ""}`,
	PHOTOS: () => "Live photo and passport-size photo",
	EDUCATION: (s) => `Graduation certificate${Number(s.computer_certificates) ? ` and ${s.computer_certificates} computer certificate(s)` : ""}`,
	ID_DOCUMENT: (s) => [s.voter_id && `Voter ID ${s.voter_id}`, s.driving_licence && `Driving licence ${s.driving_licence}`].filter(Boolean).join(", "),
};

/** Review everything, accept the declaration and submit (once, even on a double click). */
function ReviewPage() {
	const { snapshot, write } = useApplication();
	const router = useRouter();
	useStepGuard((s) => ["IN_PROGRESS", "CORRECTION_REQUIRED"].includes(s.status) && (s.next_step === "DECLARATION" || s.next_step === null));
	const [agreed, setAgreed] = useState(false);
	const { busy, error, setError, run } = useAction();

	const submit = async () => {
		if (!agreed) {
			setError("Confirm the declaration to submit");
			return;
		}
		await run(async () => {
			if (!isDone(snapshot.steps.find((s) => s.step_key === "DECLARATION"))) {
				await write("/declaration", { body: { version: DECLARATION_VERSION, accepted: true } });
			}
			await write("/submit", { idempotent: true });
			await router.push("/apply/status");
		});
	};

	const pages = pagesFor(snapshot.method).filter((p) => p.step !== "MOBILE_VERIFICATION" && p.step !== "DECLARATION" && p.step !== "VERIFICATION_METHOD");
	return (
		<ApplyShell title="Review your application" subtitle="Check your details before you submit." progress={snapshot}>
			<Section>
				{pages.map((page) => {
					const step = snapshot.steps.find((s) => s.step_key === page.step);
					const text = step?.summary ? SUMMARY[page.step]?.(step.summary) : undefined;
					return (
						<Flex key={page.path} justify="space-between" align="center" gap={3} wrap="wrap" borderBottom="1px solid" borderColor="border.hairline" pb={3}>
							<Stack spacing={0} minW={0}>
								<Text fontWeight={600}>{page.title}</Text>
								{text && <Text color="text.muted" fontSize="sm">{text}</Text>}
							</Stack>
							<Flex gap={3} align="center">
								{step && <StatusPill status={step.status} />}
								{page.step !== "CREDIT_CHECK" && step?.status !== "LOCKED" && (
									<Link as={NextLink} href={page.path} color="text.link" fontSize="sm">
										Edit
									</Link>
								)}
							</Flex>
						</Flex>
					);
				})}
			</Section>
			<Checkbox isChecked={agreed} onChange={(event) => setAgreed(event.target.checked)} alignItems="flex-start">
				I declare that the information I have given is true and complete.
			</Checkbox>
			<ErrorBanner message={error} />
			<Button onClick={submit} isLoading={busy} loadingText="Submitting">
				Submit application
			</Button>
		</ApplyShell>
	);
}

export default withApplication(ReviewPage);
