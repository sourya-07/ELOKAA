import { Button, SimpleGrid } from "@chakra-ui/react";
import { useRouter } from "next/router";
import { ApplyShell } from "@/components/ApplyShell";
import { ErrorBanner } from "@/components/ErrorBanner";
import { FileSlot } from "@/components/FileSlot";
import { TaskSection } from "@/components/TaskSection";
import { useApplication, useStepGuard } from "@/features/apply/ApplicationContext";
import { canOpen } from "@/features/apply/guards";
import { useAction } from "@/features/apply/useAction";
import { useDocuments } from "@/features/apply/useDocuments";
import { withApplication } from "@/features/apply/withApplication";
import { ApiError } from "@/lib/api";
import { missingText } from "@/lib/documents";
import { routeFor } from "@/lib/steps";
import { canChange, taskState } from "@/lib/tasks";
import { isDone } from "@/lib/types";
import { PAPER } from "@/lib/validation";

/**
 * Every document on one page, in two groups: identity documents (Aadhaar front and back as full
 * images, PAN card, both sides of the verified voter ID or driving licence) and education (class
 * 10 and 12 marksheets, the graduation certificate, an optional diploma and optional computer certificates).
 * The server's file rules decide what is missing; one "Continue" sends both groups.
 */
function DocumentsPage() {
	const { snapshot, write } = useApplication();
	const router = useRouter();
	useStepGuard((s) => canOpen(s, "KYC_DOCUMENTS") || canOpen(s, "EDUCATION"));
	const { documents, upload, remove, contentUrl, ruleFor } = useDocuments();
	const { busy, error, run } = useAction();
	const filesOf = (kind: string) => documents.filter((d) => d.kind === kind);
	const verifiedId = (snapshot.steps.find((s) => s.step_key === "ALTERNATE_ID")?.summary?.type as string | undefined) ?? "VOTER_ID";
	const idName = verifiedId === "DRIVING_LICENCE" ? "Driving licence" : "Voter ID";

	const slot = (kind: string, label: string, hint?: string, extra: { required?: boolean; allowMany?: boolean } = { required: true }) => (
		<FileSlot
			label={label}
			hint={hint}
			rule={ruleFor(kind, PAPER)}
			required={extra.required}
			allowMany={extra.allowMany}
			files={filesOf(kind)}
			onUpload={(file, onProgress, signal) => upload(kind, file, {}, onProgress, signal)}
			onRemove={remove}
			contentUrl={contentUrl}
		/>
	);

	// Both groups open once the photos are done (the server's next step names only one of them)
	const kycState = taskState(snapshot, "KYC_DOCUMENTS");
	const educationState = taskState(snapshot, "EDUCATION");
	const open = (state: ReturnType<typeof taskState>, other: ReturnType<typeof taskState>) =>
		state === "locked" && other !== "locked" ? "current" : state;

	const next = () =>
		run(async () => {
			try {
				let latest = snapshot;
				for (const [step, path] of [
					["KYC_DOCUMENTS", "/kyc-documents/submit"],
					["EDUCATION", "/education/submit"],
				] as const) {
					if (!isDone(latest.steps.find((s) => s.step_key === step))) {
						latest = await write(path);
					}
				}
				await router.push(routeFor(latest));
			} catch (e) {
				if (e instanceof ApiError && e.problem.missing_documents?.length) {
					throw new ApiError({ ...e.problem, detail: missingText(e.problem.missing_documents) });
				}
				throw e;
			}
		});

	return (
		<ApplyShell title="Your documents" subtitle="Photos or scans. Every detail must be readable." progress={snapshot}>
			<TaskSection id="identity-documents" title="Identity documents" state={open(kycState, educationState)} summary="Aadhaar, PAN card and your ID uploaded" canChange={canChange(snapshot, "KYC_DOCUMENTS")}>
				<SimpleGrid columns={{ base: 1, md: 2 }} spacing={4}>
					{slot("AADHAAR_FRONT", "Aadhaar: front", "The whole card, unmasked. Stored encrypted; only our review team can open it.")}
					{slot("AADHAAR_BACK", "Aadhaar: back")}
				</SimpleGrid>
				{slot("PAN_CARD", "PAN card")}
				<SimpleGrid columns={{ base: 1, md: 2 }} spacing={4}>
					{slot(`${verifiedId}_FRONT`, `${idName}: front`, `The ${idName.toLowerCase()} you verified.`)}
					{slot(`${verifiedId}_BACK`, `${idName}: back`)}
				</SimpleGrid>
			</TaskSection>

			<TaskSection id="education" title="Education" state={open(educationState, kycState)} summary="Marksheets and certificate uploaded" canChange={canChange(snapshot, "EDUCATION")}>
				<SimpleGrid columns={{ base: 1, md: 2 }} spacing={4}>
					{slot("CLASS_10_MARKSHEET", "Class 10 marksheet")}
					{slot("CLASS_12_MARKSHEET", "Class 12 marksheet")}
				</SimpleGrid>
				<SimpleGrid columns={{ base: 1, md: 2 }} spacing={4}>
					{slot("GRADUATION_CERTIFICATE", "Graduation certificate")}
					{slot("DIPLOMA_CERTIFICATE", "Diploma certificate", "If you have one.", { required: false })}
				</SimpleGrid>
				{slot("COMPUTER_CERTIFICATE", "Computer certificates", "Up to 5, if you have any.", { required: false, allowMany: true })}
			</TaskSection>

			<ErrorBanner message={error} />
			<Button onClick={next} isLoading={busy} alignSelf="flex-start">
				Save and continue
			</Button>
		</ApplyShell>
	);
}

export default withApplication(DocumentsPage);
