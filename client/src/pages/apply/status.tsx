import { Box, Button, Flex, Stack, Text } from "@chakra-ui/react";
import NextLink from "next/link";
import { useRouter } from "next/router";
import { ApplyShell, Section } from "@/components/ApplyShell";
import { StatusPill } from "@/components/StatusPill";
import { SuccessMark } from "@/components/SuccessMark";
import { useApplication } from "@/features/apply/ApplicationContext";
import { WithdrawPanel } from "@/features/apply/WithdrawPanel";
import { withApplication } from "@/features/apply/withApplication";
import { useI18n } from "@/i18n/I18nProvider";
import { PAGES, routeFor } from "@/lib/steps";

function StatusPage() {
	const { snapshot } = useApplication();
	const router = useRouter();
	const { t } = useI18n();
	const text = { title: t(`status.${snapshot.status}.title`), body: t(`status.${snapshot.status}.body`) };
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
				<Flex justify="space-between" align="flex-start" gap={3} wrap="wrap">
					<Stack spacing={0} minW={0}>
						<Text color="text.muted" fontSize="sm">
							{t("status.number")}
						</Text>
						{/* A long id must wrap on a phone, not widen the page */}
						<Text fontFamily="mono" fontSize="sm" overflowWrap="anywhere" translate="no">
							{snapshot.application_number ?? snapshot.application_id}
						</Text>
					</Stack>
					<StatusPill status={snapshot.status === "CORRECTION_REQUIRED" ? "REOPENED" : snapshot.status} />
				</Flex>
				{(snapshot.contact?.mobile_masked || snapshot.contact?.email_masked) && (
					<Stack as="dl" spacing={0} fontSize="sm">
						{snapshot.contact?.mobile_masked && (
							<Box>
								<Text as="dt" display="inline" color="text.muted">
									{t("status.mobile")}{" "}
								</Text>
								<Text as="dd" display="inline" fontFamily="mono" translate="no">
									+91 {snapshot.contact.mobile_masked}
								</Text>
							</Box>
						)}
						{snapshot.contact?.email_masked && (
							<Box>
								<Text as="dt" display="inline" color="text.muted">
									{t("status.email")}{" "}
								</Text>
								<Text as="dd" display="inline" fontFamily="mono" translate="no">
									{snapshot.contact.email_masked}
								</Text>
							</Box>
						)}
					</Stack>
				)}
				{submitted && <Text color="text.muted">{t("status.submittedOn", { date: submitted })}</Text>}
				{correctionDue && <Text>{t("status.correctionDue", { date: correctionDue })}</Text>}
				{reopened.map((step) => (
					<Stack key={step.step_key} spacing={1}>
						<Text fontWeight={600}>{stepTitle(step.step_key)}</Text>
						<Text>{step.reviewer_message}</Text>
					</Stack>
				))}
			</Section>
			{["SUBMITTED", "UNDER_REVIEW", "ON_HOLD"].includes(snapshot.status) && (
				<Section label={t("status.next")}>
					<Stack as="ol" spacing={2} pl={5} sx={{ listStyleType: "decimal" }}>
						<Text as="li">{t("status.next.reviewer")}</Text>
						<Text as="li">{t("status.next.approver")}</Text>
						<Text as="li">{t("status.next.outcome")}</Text>
					</Stack>
				</Section>
			)}
			{["IN_PROGRESS", "CORRECTION_REQUIRED"].includes(snapshot.status) && (
				<Button onClick={() => router.push(routeFor(snapshot))}>{t("status.continue")}</Button>
			)}
			{snapshot.available_actions.includes("WITHDRAW") && <WithdrawPanel />}
			{snapshot.status === "EXPIRED" && (
				<Button as={NextLink} href="/apply/continue">
					{t("status.startNew")}
				</Button>
			)}
		</ApplyShell>
	);
}

export default withApplication(StatusPage);
