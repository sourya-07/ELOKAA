import { Button, Stack, Text } from "@chakra-ui/react";
import NextLink from "next/link";
import { useRouter } from "next/router";
import { ApplyShell, Section } from "@/components/ApplyShell";
import { StatusPill } from "@/components/StatusPill";
import { SuccessMark } from "@/components/SuccessMark";
import { useApplication } from "@/features/apply/ApplicationContext";
import { WithdrawPanel } from "@/features/apply/WithdrawPanel";
import { withApplication } from "@/features/apply/withApplication";
import { PAGES, routeFor } from "@/lib/steps";
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
	const stepTitle = (key: string) => PAGES.find((p) => p.step === key)?.title ?? key.toLowerCase().replace(/_/g, " ");
	const date = (value: string | null) =>
		value ? new Date(value).toLocaleDateString("en-IN", { day: "numeric", month: "long", year: "numeric" }) : null;
	const submitted = date(snapshot.submitted_at);
	const correctionDue = snapshot.status === "CORRECTION_REQUIRED" ? date(snapshot.expires_at) : null;
	return (
		<ApplyShell
			signedIn
			title={text.title}
			subtitle={text.body}
			mark={snapshot.status === "SUBMITTED" || snapshot.status === "APPROVED" ? <SuccessMark /> : undefined}
		>
			<Section>
				<Stack direction="row" justify="space-between">
					<Text color="text.muted">Application {snapshot.application_id}</Text>
					<StatusPill status={snapshot.status === "CORRECTION_REQUIRED" ? "REOPENED" : snapshot.status} />
				</Stack>
				{submitted && <Text color="text.muted">Submitted on {submitted}</Text>}
				{correctionDue && <Text>Please make the changes by {correctionDue}, or the application will expire.</Text>}
				{reopened.map((step) => (
					<Stack key={step.step_key} spacing={1}>
						<Text fontWeight={600}>{stepTitle(step.step_key)}</Text>
						<Text>{step.reviewer_message}</Text>
					</Stack>
				))}
			</Section>
			{["SUBMITTED", "UNDER_REVIEW", "ON_HOLD"].includes(snapshot.status) && (
				<Section label="What happens next">
					<Stack as="ol" spacing={2} pl={5} sx={{ listStyleType: "decimal" }}>
						<Text as="li">A reviewer checks your details and documents.</Text>
						<Text as="li">A second person in our team approves it (two people always look at an application).</Text>
						<Text as="li">We tell you the outcome on your mobile and email. If something needs a change, you will see it here.</Text>
					</Stack>
				</Section>
			)}
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
