import { FormControl, FormHelperText, FormLabel, Input, SimpleGrid } from "@chakra-ui/react";
import { useState } from "react";
import { ApplyShell, Section } from "@/components/ApplyShell";
import { FileSlot } from "@/components/FileSlot";
import { useApplication, useStepGuard } from "@/features/apply/ApplicationContext";
import { canOpen } from "@/features/apply/guards";
import { SubmitStep } from "@/features/apply/SubmitStep";
import { useDocuments } from "@/features/apply/useDocuments";
import { withApplication } from "@/features/apply/withApplication";
import { position } from "@/lib/steps";
import { PAPER } from "@/lib/validation";

/** Voter ID or driving licence: at least one, each with its number and both sides. */
function IdDocumentPage() {
	const { snapshot } = useApplication();
	useStepGuard((s) => canOpen(s, "ID_DOCUMENT"));
	const { documents, upload, remove, contentUrl } = useDocuments();
	const [voter, setVoter] = useState("");
	const [licence, setLicence] = useState("");
	const slot = (kind: string, label: string) => (
		<FileSlot
			label={label}
			rule={PAPER}
			files={documents.filter((d) => d.kind === kind)}
			onUpload={(file) => upload(kind, file)}
			onRemove={remove}
			contentUrl={contentUrl}
		/>
	);
	return (
		<ApplyShell title="Voter ID or driving licence" subtitle="Add at least one. You can add both." step={position(snapshot.method, "/apply/id-document")}>
			<Section label="Voter ID">
				<FormControl>
					<FormLabel htmlFor="voter">EPIC number</FormLabel>
					<Input id="voter" autoCapitalize="characters" maxLength={10} value={voter} onChange={(event) => setVoter(event.target.value.toUpperCase())} />
					<FormHelperText>10 characters, for example ABC1234567.</FormHelperText>
				</FormControl>
				<SimpleGrid columns={{ base: 1, md: 2 }} spacing={4}>
					{slot("VOTER_ID_FRONT", "Front")}
					{slot("VOTER_ID_BACK", "Back")}
				</SimpleGrid>
			</Section>
			<Section label="Driving licence">
				<FormControl>
					<FormLabel htmlFor="licence">Licence number</FormLabel>
					<Input id="licence" autoCapitalize="characters" maxLength={18} value={licence} onChange={(event) => setLicence(event.target.value.toUpperCase())} />
					<FormHelperText>For example MH01 20110012345.</FormHelperText>
				</FormControl>
				<SimpleGrid columns={{ base: 1, md: 2 }} spacing={4}>
					{slot("DRIVING_LICENCE_FRONT", "Front")}
					{slot("DRIVING_LICENCE_BACK", "Back")}
				</SimpleGrid>
			</Section>
			<SubmitStep
				path="/id-document/submit"
				body={{ voter_id_number: voter.trim() || null, driving_licence_number: licence.trim() || null }}
			/>
		</ApplyShell>
	);
}

export default withApplication(IdDocumentPage);
