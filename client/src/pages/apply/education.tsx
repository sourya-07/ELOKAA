import { ApplyShell, Section } from "@/components/ApplyShell";
import { FileSlot } from "@/components/FileSlot";
import { useApplication, useStepGuard } from "@/features/apply/ApplicationContext";
import { canOpen } from "@/features/apply/guards";
import { SubmitStep } from "@/features/apply/SubmitStep";
import { useDocuments } from "@/features/apply/useDocuments";
import { withApplication } from "@/features/apply/withApplication";
import { PAPER } from "@/lib/validation";

/** Graduation certificate (required) and computer certificates (optional, up to 5). */
function EducationPage() {
	const { snapshot } = useApplication();
	useStepGuard((s) => canOpen(s, "EDUCATION"));
	const { documents, upload, remove, contentUrl } = useDocuments();
	return (
		<ApplyShell title="Education certificates" subtitle="Your graduation certificate. Add computer certificates if you have them." progress={snapshot}>
			<Section>
				<FileSlot
					label="Graduation certificate"
					rule={PAPER}
					required
					files={documents.filter((d) => d.kind === "GRADUATION_CERTIFICATE")}
					onUpload={(file) => upload("GRADUATION_CERTIFICATE", file)}
					contentUrl={contentUrl}
				/>
				<FileSlot
					label="Computer certificates"
					rule={PAPER}
					allowMany
					files={documents.filter((d) => d.kind === "COMPUTER_CERTIFICATE")}
					onUpload={(file) => upload("COMPUTER_CERTIFICATE", file)}
					onRemove={remove}
					contentUrl={contentUrl}
				/>
			</Section>
			<SubmitStep path="/education/submit" />
		</ApplyShell>
	);
}

export default withApplication(EducationPage);
