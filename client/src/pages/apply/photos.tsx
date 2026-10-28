import { Stack, Text } from "@chakra-ui/react";
import { ApplyShell, Section } from "@/components/ApplyShell";
import { CameraCapture } from "@/components/CameraCapture";
import { FileSlot } from "@/components/FileSlot";
import { StatusPill } from "@/components/StatusPill";
import { useApplication, useStepGuard } from "@/features/apply/ApplicationContext";
import { canOpen } from "@/features/apply/guards";
import { SubmitStep } from "@/features/apply/SubmitStep";
import { useDocuments } from "@/features/apply/useDocuments";
import { withApplication } from "@/features/apply/withApplication";
import { PHOTO } from "@/lib/validation";

/** Live photo (camera) and passport-size photo (upload), JPG only. */
function PhotosPage() {
	const { snapshot } = useApplication();
	useStepGuard((s) => canOpen(s, "PHOTOS"));
	const { documents, upload, contentUrl } = useDocuments();
	const live = documents.filter((d) => d.kind === "LIVE_PHOTO");
	return (
		<ApplyShell title="Your photos" subtitle="A live photo taken now, and a passport-size photo." progress={snapshot}>
			<Section label="Live photo">
				{live.map((file) => (
					<Stack key={file.document_id} direction="row" justify="space-between" align="center">
						<Text>Photo taken</Text>
						<StatusPill status={file.status} />
					</Stack>
				))}
				<CameraCapture onCapture={(file) => upload("LIVE_PHOTO", file)} />
			</Section>
			<Section label="Passport-size photo">
				<FileSlot
					label="Passport-size photo"
					rule={PHOTO}
					required
					files={documents.filter((d) => d.kind === "PASSPORT_PHOTO")}
					onUpload={(file) => upload("PASSPORT_PHOTO", file)}
					contentUrl={contentUrl}
				/>
			</Section>
			<SubmitStep path="/photos/submit" />
		</ApplyShell>
	);
}

export default withApplication(PhotosPage);
