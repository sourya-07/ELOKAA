import { Button, Text } from "@chakra-ui/react";
import { useRouter } from "next/router";
import { ApplyShell } from "@/components/ApplyShell";
import { TaskSection } from "@/components/TaskSection";
import { AddressForm } from "@/features/apply/AddressForm";
import { useApplication, useStepGuard } from "@/features/apply/ApplicationContext";
import { DigiLockerTask } from "@/features/apply/DigiLockerTask";
import { EligibilityTask } from "@/features/apply/EligibilityTask";
import { canOpen } from "@/features/apply/guards";
import { PanForm } from "@/features/apply/PanForm";
import { withApplication } from "@/features/apply/withApplication";
import { routeFor } from "@/lib/steps";
import { canChange, taskState } from "@/lib/tasks";
import type { StepKey } from "@/lib/types";

const STEPS: StepKey[] = ["AADHAAR", "HOME_ADDRESS", "PAN", "CREDIT_CHECK"];

/**
 * Identity and eligibility on one page, as a list of tasks in the server's order: Aadhaar through
 * DigiLocker (with the home address it gives), PAN, then the credit check. The task to do now is
 * open; done ones fold to a line; later ones say what they wait for.
 */
function IdentityPage() {
	const { snapshot } = useApplication();
	const router = useRouter();
	useStepGuard((s) => STEPS.some((key) => canOpen(s, key)));
	const summary = (key: StepKey) => snapshot.steps.find((s) => s.step_key === key)?.summary ?? {};
	const aadhaar = summary("AADHAAR") as { aadhaar_masked?: string };
	const address = summary("HOME_ADDRESS") as { state?: string; pincode?: string; source?: string };
	const pan = summary("PAN") as { pan_masked?: string };
	const addressState = taskState(snapshot, "HOME_ADDRESS");
	const nextElsewhere = snapshot.next_step && !STEPS.includes(snapshot.next_step);

	return (
		<ApplyShell title="Your identity and eligibility" subtitle="Aadhaar through DigiLocker, your PAN, then the credit check." progress={snapshot}>
			<TaskSection
				id="aadhaar"
				title="Aadhaar, through DigiLocker"
				state={taskState(snapshot, "AADHAAR")}
				summary={`Aadhaar ${aadhaar.aadhaar_masked ?? ""} · from DigiLocker`}
				canChange={canChange(snapshot, "AADHAAR")}
			>
				<DigiLockerTask />
			</TaskSection>

			{(addressState !== "locked" || taskState(snapshot, "AADHAAR") === "done") && (
				<TaskSection
					id="address"
					title="Home address"
					state={addressState}
					summary={`${address.state ?? ""} ${address.pincode ?? ""} · ${address.source === "DIGILOCKER" ? "from DigiLocker" : "added by you"}`}
					canChange={canChange(snapshot, "HOME_ADDRESS") && address.source !== "DIGILOCKER"}
				>
					<Text>DigiLocker did not give your complete address. Add where you live now.</Text>
					<AddressForm />
				</TaskSection>
			)}

			<TaskSection
				id="pan"
				title="PAN"
				state={taskState(snapshot, "PAN")}
				summary={`PAN ${pan.pan_masked ?? ""}`}
				lockedReason="After your Aadhaar is verified"
				canChange={canChange(snapshot, "PAN")}
			>
				<PanForm />
			</TaskSection>

			<TaskSection
				id="eligibility"
				title="Eligibility (credit check)"
				state={taskState(snapshot, "CREDIT_CHECK")}
				summary="Eligible: your CIBIL score meets 700"
				lockedReason="After your PAN is verified"
			>
				<EligibilityTask />
			</TaskSection>

			{nextElsewhere && (
				<Button onClick={() => router.push(routeFor(snapshot))} alignSelf="flex-start">
					Continue
				</Button>
			)}
		</ApplyShell>
	);
}

export default withApplication(IdentityPage);
