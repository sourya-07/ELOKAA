import {
	AlertDialog,
	AlertDialogBody,
	AlertDialogContent,
	AlertDialogFooter,
	AlertDialogHeader,
	AlertDialogOverlay,
	Box,
	Button,
	Checkbox,
	Flex,
	Heading,
	Link,
	ListItem,
	Stack,
	Text,
	UnorderedList,
	useDisclosure,
} from "@chakra-ui/react";
import NextLink from "next/link";
import { useRouter } from "next/router";
import { useRef, useState } from "react";
import { ApplyShell, Section } from "@/components/ApplyShell";
import { ErrorBanner } from "@/components/ErrorBanner";
import { StatusPill } from "@/components/StatusPill";
import { useApplication, useStepGuard } from "@/features/apply/ApplicationContext";
import { useAction } from "@/features/apply/useAction";
import { withApplication } from "@/features/apply/withApplication";
import { describeBlocker } from "@/lib/blockers";
import { DECLARATION_VERSION } from "@/lib/consent";
import { MILESTONES } from "@/lib/milestones";
import { PAGES } from "@/lib/steps";
import { canChange } from "@/lib/tasks";
import { isDone, type Snapshot } from "@/lib/types";

const SUMMARY: Record<string, (s: Record<string, unknown>, snapshot: Snapshot) => string> = {
	EMAIL_VERIFICATION: (s, snapshot) =>
		[snapshot.contact?.mobile_masked && `Mobile +91 ${snapshot.contact.mobile_masked}`, `Email ${s.email_masked ?? snapshot.contact?.email_masked ?? ""}`]
			.filter(Boolean)
			.join(" · "),
	AADHAAR: (s) => `Aadhaar ${s.aadhaar_masked ?? ""} from DigiLocker`,
	HOME_ADDRESS: (s) => `${s.state ?? ""} ${s.pincode ?? ""}${s.source === "DIGILOCKER" ? " (from DigiLocker)" : ""}`,
	PAN: (s) => `PAN ${s.pan_masked ?? ""}`,
	CREDIT_CHECK: () => "Eligible: CIBIL score meets 700",
	ALTERNATE_ID: (s) => `${s.type === "DRIVING_LICENCE" ? "Driving licence" : "Voter ID"} ${s.number_masked ?? ""}`,
	PHOTOS: () => "Live photo (with location) and passport-size photo",
	KYC_DOCUMENTS: (s) => `Aadhaar front and back, PAN card, ${s.id_document === "DRIVING_LICENCE" ? "driving licence" : "voter ID"}`,
	EDUCATION: (s) =>
		`Class 10 and 12 marksheets, graduation certificate${s.diploma ? ", diploma" : ""}${Number(s.computer_certificates) ? `, ${s.computer_certificates} computer certificate(s)` : ""}`,
};

/** Where a done part's data came from: checked with a record, supplied by the applicant, or waiting for our team. */
function sourceOf(key: string, summary: Record<string, unknown>): string | null {
	switch (key) {
		case "EMAIL_VERIFICATION":
			return "Verified with codes";
		case "AADHAAR":
			return "Verified through DigiLocker";
		case "HOME_ADDRESS":
			return summary.source === "DIGILOCKER" ? "From DigiLocker" : "Added by you";
		case "PAN":
		case "ALTERNATE_ID":
			return "Verified with the government record";
		case "CREDIT_CHECK":
			return "Checked with the credit bureau";
		case "PHOTOS":
		case "KYC_DOCUMENTS":
		case "EDUCATION":
			return "Uploaded by you; our team will check it";
		default:
			return null;
	}
}

/** "Change" and "Fix" go to the right part of the page and bring the applicant back here. */
function changeLink(path: string): string {
	const [base, anchor] = path.split("#");
	return `${base}?return=review${anchor ? `#${anchor}` : ""}`;
}

/**
 * Everything at a glance before submitting: what still stops submission comes first, each with a
 * link to fix it (which brings the applicant back here); then every part, grouped as the progress
 * line names them. Submitting asks once more, then sends one request (a repeat replays it).
 */
function ReviewPage() {
	const { snapshot, write } = useApplication();
	const router = useRouter();
	useStepGuard((s) => ["IN_PROGRESS", "CORRECTION_REQUIRED"].includes(s.status));
	const [agreed, setAgreed] = useState(false);
	const confirm = useDisclosure();
	const cancel = useRef<HTMLButtonElement>(null);
	const { busy, error, setError, run } = useAction();
	const declarationDone = isDone(snapshot.steps.find((s) => s.step_key === "DECLARATION"));
	const blockers = (snapshot.submit_blockers ?? []).filter((b) => b.step_key !== "DECLARATION");

	const ask = () => {
		if (!agreed && !declarationDone) {
			setError("Confirm the declaration to submit");
			return;
		}
		setError(null);
		confirm.onOpen();
	};

	const submit = () =>
		run(async () => {
			if (!declarationDone) {
				await write("/declaration", { body: { version: DECLARATION_VERSION, accepted: true } });
			}
			await write("/submit", { idempotent: true });
			confirm.onClose();
			await router.push("/apply/status");
		});

	return (
		<ApplyShell title="Review your application" subtitle="Check your details before you submit." progress={snapshot}>
			{blockers.length > 0 && (
				<Section label="Still to do before you can submit">
					<UnorderedList spacing={2} pl={1}>
						{blockers.map((blocker) => {
							const item = describeBlocker(blocker);
							return (
								<ListItem key={`${blocker.step_key}-${blocker.group ?? blocker.code}`}>
									<Text as="span" fontWeight={600}>
										{item.title}:
									</Text>{" "}
									{item.text}{" "}
									<Link as={NextLink} href={item.href} color="text.link">
										Fix
									</Link>
								</ListItem>
							);
						})}
					</UnorderedList>
				</Section>
			)}

			{MILESTONES.filter((m) => m.key !== "REVIEW").map((milestone) => {
				const pages = PAGES.filter((p) => milestone.steps.includes(p.step) && p.step !== "DECLARATION");
				return (
					<Section key={milestone.key} label={milestone.label}>
						{pages.map((page) => {
							const step = snapshot.steps.find((s) => s.step_key === page.step);
							if (!step) {
								return null;
							}
							const text = step.summary ? SUMMARY[page.step]?.(step.summary, snapshot) : undefined;
							const source = isDone(step) ? sourceOf(page.step, step.summary ?? {}) : null;
							const editable = canChange(snapshot, page.step);
							return (
								<Flex key={page.path} justify="space-between" align="center" gap={3} wrap="wrap">
									<Stack spacing={0} minW={0}>
										<Text fontWeight={600}>{page.title}</Text>
										{text && (
											<Text color="text.muted" fontSize="sm" fontFamily={/\*{2,}|X{4}/.test(text) ? "mono" : undefined}>
												{text}
											</Text>
										)}
										{source && (
											<Text color="text.muted" fontSize="xs">
												{source}
											</Text>
										)}
										{step.reviewer_message && (
											<Text fontSize="sm" color="status.warn.fg">
												Our team: {step.reviewer_message}
											</Text>
										)}
									</Stack>
									<Flex gap={3} align="center">
										<StatusPill status={step.status} />
										{editable && (
											<Link as={NextLink} href={changeLink(page.path)} color="text.link" fontSize="sm">
												Change
											</Link>
										)}
									</Flex>
								</Flex>
							);
						})}
					</Section>
				);
			})}

			<Box>
				<Checkbox isChecked={agreed || declarationDone} isDisabled={declarationDone} onChange={(event) => setAgreed(event.target.checked)} alignItems="flex-start">
					I declare that the information I have given is true and complete.
				</Checkbox>
			</Box>
			<ErrorBanner message={error} />
			<Button onClick={ask} isDisabled={blockers.length > 0} alignSelf="flex-start">
				Submit application
			</Button>
			{blockers.length > 0 && (
				<Text fontSize="sm" color="text.muted">
					You can submit once everything above is done.
				</Text>
			)}

			<AlertDialog isOpen={confirm.isOpen} leastDestructiveRef={cancel} onClose={confirm.onClose}>
				<AlertDialogOverlay>
					<AlertDialogContent mx={4}>
						<AlertDialogHeader>
							<Heading as="h2" size="md">
								Submit your application?
							</Heading>
						</AlertDialogHeader>
						<AlertDialogBody>
							<Stack spacing={2}>
								<Text>After you submit, you cannot change your details unless our team asks you to.</Text>
								<Text color="text.muted">Two people in our team review every application. You can follow it on your status page.</Text>
								<ErrorBanner message={error} />
							</Stack>
						</AlertDialogBody>
						<AlertDialogFooter gap={3}>
							<Button ref={cancel} variant="outline" onClick={confirm.onClose} isDisabled={busy}>
								Not yet
							</Button>
							<Button onClick={submit} isLoading={busy} loadingText="Submitting">
								Submit
							</Button>
						</AlertDialogFooter>
					</AlertDialogContent>
				</AlertDialogOverlay>
			</AlertDialog>
		</ApplyShell>
	);
}

export default withApplication(ReviewPage);
