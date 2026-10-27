import { ApplyShell, Section } from "@/components/ApplyShell";
import { useApplication, useStepGuard } from "@/features/apply/ApplicationContext";
import { canOpen } from "@/features/apply/guards";
import { PanForm } from "@/features/apply/PanForm";
import { withApplication } from "@/features/apply/withApplication";
import { position } from "@/lib/steps";

function PanPage() {
	const { snapshot } = useApplication();
	useStepGuard((s) => canOpen(s, "PAN") && s.method === "MANUAL");
	return (
		<ApplyShell title="PAN" subtitle="We check your PAN with the Income Tax records." step={position(snapshot.method, "/apply/pan")}>
			<Section>
				<PanForm askPerson />
			</Section>
		</ApplyShell>
	);
}

export default withApplication(PanPage);
