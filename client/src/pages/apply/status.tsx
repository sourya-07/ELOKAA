import { Button, Stack, Text } from "@chakra-ui/react";
import NextLink from "next/link";
import { useRouter } from "next/router";
import { ApplyShell, Section } from "@/components/ApplyShell";
import { StatusPill } from "@/components/StatusPill";
import { SuccessMark } from "@/components/SuccessMark";
import { useApplication } from "@/features/apply/ApplicationContext";
import { WithdrawPanel } from "@/features/apply/WithdrawPanel";
import { withApplication } from "@/features/apply/withApplication";
import { routeFor } from "@/lib/steps";
import type { ApplicationStatus } from "@/lib/types";

const TEXT: Record<ApplicationStatus, { title: string; body: string }> = {
	IN_PROGRESS: { title: "Your application is in progress", body: "Continue where you left off." },
	SUBMITTED: { title: "Application submitted", body: "Our team will check your details and documents. We will contact you on your mobile." },
	UNDER_REVIEW: { title: "We are checking your application", body: "Our team is looking at your details and documents." },
	ON_HOLD: { title: "We are checking your application", body: "Our team needs a little more time. We will contact you." },
	CORRECTION_REQUIRED: { title: "Some changes are needed", body: "Our team has asked you to change the steps below." },
	APPROVED: { title: "You are an ELOKA partner", body: "Your application is approved. Our team will contact you about the next steps." },
	REJECTED: { title: "Your application was not approved", body: "Our team could not approve your application. You can contact support for details." },
	INELIGIBLE: { title: "You are not eligible right now", body: "Your credit check did not meet our requirement, so we cannot go on with this application." },
	WITHDRAWN: { title: "Application withdrawn", body: "You withdrew this application." },
	EXPIRED: { title: "Application expired", body: "This application expired because it was not finished in time. You can start a new one." },
};

function StatusPage() {
	const { snapshot } = useApplication();
	const router = useRouter();
	const text = TEXT[snapshot.status];
	const reopened = snapshot.steps.filter((s) => s.status === "REOPENED" && s.reviewer_message);
	return (
		<ApplyShell
			title={text.title}
			subtitle={text.body}
			mark={snapshot.status === "SUBMITTED" || snapshot.status === "APPROVED" ? <SuccessMark /> : undefined}
		>
			<Section>
				<Stack direction="row" justify="space-between">
					<Text color="text.muted">Application {snapshot.application_id}</Text>
					<StatusPill status={snapshot.status === "CORRECTION_REQUIRED" ? "REOPENED" : snapshot.status} />
				</Stack>
				{reopened.map((step) => (
					<Stack key={step.step_key} spacing={1}>
						<Text fontWeight={600}>{step.step_key.toLowerCase().replace(/_/g, " ")}</Text>
						<Text>{step.reviewer_message}</Text>
					</Stack>
				))}
			</Section>
			{["IN_PROGRESS", "CORRECTION_REQUIRED"].includes(snapshot.status) && (
				<Button onClick={() => router.push(routeFor(snapshot))}>Continue</Button>
			)}
			{snapshot.available_actions.includes("WITHDRAW") && <WithdrawPanel />}
			{snapshot.status === "EXPIRED" && (
				<Button as={NextLink} href="/apply/continue">
					Start a new application
				</Button>
			)}
		</ApplyShell>
	);
}

export default withApplication(StatusPage);
