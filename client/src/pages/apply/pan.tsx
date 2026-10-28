import { ApplyShell, Section } from "@/components/ApplyShell";
import { useApplication, useStepGuard } from "@/features/apply/ApplicationContext";
import { canOpen } from "@/features/apply/guards";
import { PanForm } from "@/features/apply/PanForm";
import { withApplication } from "@/features/apply/withApplication";

function PanPage() {
	const { snapshot } = useApplication();
	useStepGuard((s) => canOpen(s, "PAN") && s.method === "MANUAL");
	return (
		<ApplyShell title="PAN" subtitle="We check your PAN with the Income Tax records." progress={snapshot}>
			<Section>
				<PanForm askPerson />
			</Section>
		</ApplyShell>
	);
}

export default withApplication(PanPage);
