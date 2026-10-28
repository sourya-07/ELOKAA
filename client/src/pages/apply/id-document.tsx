import { SimpleGrid, Text } from "@chakra-ui/react";
import { ApplyShell, Section } from "@/components/ApplyShell";
import { FileSlot } from "@/components/FileSlot";
import { useApplication, useStepGuard } from "@/features/apply/ApplicationContext";
import { canOpen } from "@/features/apply/guards";
import { SubmitStep } from "@/features/apply/SubmitStep";
import { useDocuments } from "@/features/apply/useDocuments";
import { withApplication } from "@/features/apply/withApplication";
import { PAPER } from "@/lib/validation";

/** Both sides of the voter ID or driving licence that was verified on the identity page. */
function IdDocumentPage() {
	const { snapshot } = useApplication();
	useStepGuard((s) => canOpen(s, "ID_DOCUMENT"));
	const { documents, upload, remove, contentUrl } = useDocuments();
	const verified = (snapshot.steps.find((s) => s.step_key === "ALTERNATE_ID")?.summary?.type as string | undefined) ?? "VOTER_ID";
	const prefix = verified === "DRIVING_LICENCE" ? "DRIVING_LICENCE" : "VOTER_ID";
	const name = verified === "DRIVING_LICENCE" ? "driving licence" : "voter ID";
	const slot = (kind: string, label: string) => (
		<FileSlot label={label} rule={PAPER} required files={documents.filter((d) => d.kind === kind)} onUpload={(file) => upload(kind, file)} onRemove={remove} contentUrl={contentUrl} />
	);
	return (
		<ApplyShell title={`Your ${name}: both sides`} subtitle={`Photos or scans of the ${name} you verified.`} progress={snapshot}>
			<Section>
				<Text color="text.muted" fontSize="sm">
					The whole card must be readable, without glare or cut-off edges.
				</Text>
				<SimpleGrid columns={{ base: 1, md: 2 }} spacing={4}>
					{slot(`${prefix}_FRONT`, "Front")}
					{slot(`${prefix}_BACK`, "Back")}
				</SimpleGrid>
			</Section>
			<SubmitStep path="/id-document/submit" />
		</ApplyShell>
	);
}

export default withApplication(IdDocumentPage);
