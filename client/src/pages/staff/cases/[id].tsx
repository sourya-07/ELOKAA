import {
	Box,
	Button,
	Checkbox,
	CheckboxGroup,
	Flex,
	FormControl,
	FormLabel,
	Grid,
	Heading,
	Input,
	Link,
	List,
	ListItem,
	Select,
	Stack,
	Text,
	Textarea,
	Wrap,
} from "@chakra-ui/react";
import { useRouter } from "next/router";
import { type ReactNode, useCallback, useEffect, useState } from "react";
import { ErrorBanner } from "@/components/ErrorBanner";
import { StatusPill } from "@/components/StatusPill";
import { useAction } from "@/features/apply/useAction";
import { StaffShell } from "@/features/staff/StaffShell";
import type { CaseView, StaffView } from "@/features/staff/types";
import { call } from "@/lib/api";

const REASONS = ["BLURRY", "WRONG_DOCUMENT", "NAME_MISMATCH", "EXPIRED_DOCUMENT", "FULL_AADHAAR_VISIBLE", "TAMPERED", "OTHER"];

function Panel({ title, children }: { title: string; children: ReactNode }) {
	return (
		<Box bg="bg.surface" border="1px solid" borderColor="border.hairline" borderRadius="card" p={5}>
			<Heading as="h2" size="sm" mb={4}>
				{title}
			</Heading>
			<Stack spacing={3}>{children}</Stack>
		</Box>
	);
}

/** One application for review: evidence with decisions, then the case actions allowed for the staff member's roles. */
export default function CasePage() {
	const router = useRouter();
	const id = typeof router.query.id === "string" ? router.query.id : null;
	const [view, setView] = useState<CaseView | null>(null);
	const [reason, setReason] = useState(REASONS[0]);
	const [correction, setCorrection] = useState<{ steps: string[]; message: string }>({ steps: [], message: "" });
	const [reveal, setReveal] = useState<{ field: string; reason: string; value?: string }>({ field: "PAN", reason: "" });
	// Approve and reject ask for a second click: they close the application
	const [confirming, setConfirming] = useState<"approve" | "reject" | null>(null);
	const [unblockNote, setUnblockNote] = useState("");
	const { busy, error, run } = useAction();
	const base = `/v1/admin/applications/${id}`;

	const load = useCallback(async () => {
		if (id) {
			setView((await call<CaseView>(base)).data);
		}
	}, [base, id]);
	useEffect(() => {
		load().catch(() => undefined);
	}, [load]);

	const act = (path: string, body?: unknown) =>
		run(async () => {
			const reply = await call<CaseView>(base + path, { method: "POST", body: body ?? {} });
			setView(reply.data);
		});

	return (
		<StaffShell title={id ? `Application ${id}` : "Application"}>
			{(me: StaffView) => {
				if (!view) {
					return <Text color="text.muted">Loading…</Text>;
				}
				const reviewer = me.roles.includes("REVIEWER");
				const approver = me.roles.includes("APPROVER");
				const status = view.application.status;
				const mine = view.assigned_to_id === me.id && status === "UNDER_REVIEW";
				return (
					<Stack spacing={6}>
						<Flex gap={3} align="center" wrap="wrap">
							<StatusPill status={status} />
							{view.application.application_number && (
								<Text fontFamily="mono" translate="no">
									No. {view.application.application_number}
								</Text>
							)}
							<Text color="text.muted">{view.application.method === "DIGILOCKER" ? "DigiLocker" : "Step by step"}</Text>
							<Text color="text.muted">Credit check: {view.eligibility.toLowerCase().replace(/_/g, " ")}</Text>
							<Text color="text.muted">Reviewer: {view.assigned_to ?? "nobody"}</Text>
						</Flex>
						<ErrorBanner message={error} />
						<Grid templateColumns={{ base: "1fr", lg: "2fr 1fr" }} gap={6}>
							<Stack spacing={6} minW={0}>
								<Panel title="Evidence">
									<Flex gap={2} align="center" wrap="wrap">
										<Text fontSize="sm">Reason when rejecting:</Text>
										<Select size="sm" maxW="240px" value={reason} onChange={(event) => setReason(event.target.value)} aria-label="Reason">
											{REASONS.map((r) => (
												<option key={r}>{r}</option>
											))}
										</Select>
									</Flex>
									{view.evidence.map((item) => (
										<Flex key={item.id} justify="space-between" align="center" gap={3} wrap="wrap" borderTop="1px solid" borderColor="border.hairline" pt={3}>
											<Stack spacing={0} minW={0}>
												<Text fontWeight={600}>{item.kind.replace(/_/g, " ").toLowerCase()}</Text>
												<Text fontSize="sm" color="text.muted">
													{item.value ?? ""} {item.reason_code ? `· ${item.reason_code}` : ""}
												</Text>
												{item.type === "DOCUMENT" && (
													<Link href={`/api${base}/documents/${item.id}/content`} isExternal color="text.link" fontSize="sm">
														Open file
													</Link>
												)}
											</Stack>
											<Flex gap={2} align="center">
												<StatusPill status={item.status} />
												{reviewer && mine && item.status !== "VERIFIED" && item.status !== "VERIFICATION_FAILED" && (
													<>
														<Button size="sm" onClick={() => act(`/${item.type === "DOCUMENT" ? "documents" : "verifications"}/${item.id}/decision`, { verified: true })} isDisabled={busy}>
															Verify
														</Button>
														<Button size="sm" variant="outline" onClick={() => act(`/${item.type === "DOCUMENT" ? "documents" : "verifications"}/${item.id}/decision`, { verified: false, reason_code: reason })} isDisabled={busy}>
															Reject
														</Button>
													</>
												)}
											</Flex>
										</Flex>
									))}
									{view.address.state && (
										<Flex justify="space-between" align="center" gap={3} wrap="wrap" borderTop="1px solid" borderColor="border.hairline" pt={3}>
											<Stack spacing={0}>
												<Text fontWeight={600}>home address</Text>
												<Text fontSize="sm" color="text.muted">
													{view.address.state} {view.address.pincode} · {view.address.source?.toLowerCase().replace(/_/g, " ")}
												</Text>
											</Stack>
											<Flex gap={2}>
												<StatusPill status={view.address.status} />
												{reviewer && mine && view.address.status !== "VERIFIED" && (
													<>
														<Button size="sm" onClick={() => act("/address/decision", { verified: true })} isDisabled={busy}>
															Verify
														</Button>
														<Button size="sm" variant="outline" onClick={() => act("/address/decision", { verified: false, reason_code: reason })} isDisabled={busy}>
															Reject
														</Button>
													</>
												)}
											</Flex>
										</Flex>
									)}
									{view.photo_location && (
										<Stack spacing={0} borderTop="1px solid" borderColor="border.hairline" pt={3}>
											<Text fontWeight={600}>live photo location</Text>
											<Text fontSize="sm" color="text.muted">
												{view.photo_location.latitude.toFixed(5)}, {view.photo_location.longitude.toFixed(5)} · within about {view.photo_location.accuracy_m} m ·{" "}
												{new Date(view.photo_location.captured_at).toLocaleString("en-IN")}
											</Text>
											{view.photo_location.approximate_address && (
												<Text fontSize="sm">Approximate address (OpenStreetMap, not checked): {view.photo_location.approximate_address}</Text>
											)}
											{view.photo_location.flags && (
												<Text fontSize="sm" color="status.warn.fg">
													{view.photo_location.flags.split(",").map((f) => f.toLowerCase().replace(/_/g, " ")).join(", ")} (the browser&apos;s location: a sign, not proof)
												</Text>
											)}
										</Stack>
									)}
									{Object.entries(view.id_numbers).map(([type, masked]) => (
										<Text key={type} fontSize="sm">
											{type.replace(/_/g, " ").toLowerCase()}: {masked}
										</Text>
									))}
								</Panel>
								<Panel title="History">
									<List spacing={2}>
										{view.actions.map((a, index) => (
											<ListItem key={index} fontSize="sm">
												{new Date(a.occurred_at).toLocaleString("en-IN")} · {a.staff} · {a.action.toLowerCase().replace(/_/g, " ")}
												{a.decision ? ` · ${a.decision.toLowerCase()}` : ""}
												{a.reason_code ? ` · ${a.reason_code}` : ""}
											</ListItem>
										))}
									</List>
								</Panel>
							</Stack>
							<Stack spacing={6} minW={0}>
								{["IN_PROGRESS", "CORRECTION_REQUIRED"].includes(status) && view.application.steps.some((step) => step.status === "BLOCKED") && (
									<Panel title="Blocked steps">
										<Text fontSize="sm" color="text.muted">
											Too many failed tries. Speak to the applicant first; unblocking gives the step its tries back.
										</Text>
										<Input size="sm" placeholder="What you agreed with the applicant" value={unblockNote} onChange={(event) => setUnblockNote(event.target.value)} aria-label="Note" />
										{view.application.steps
											.filter((step) => step.status === "BLOCKED")
											.map((step) => (
												<Flex key={step.step_key} justify="space-between" align="center" gap={3} wrap="wrap">
													<Text fontSize="sm">
														{step.step_key.toLowerCase().replace(/_/g, " ")} · {step.last_error_code?.toLowerCase().replace(/_/g, " ")}
													</Text>
													<Button
														size="sm"
														variant="outline"
														isDisabled={step.step_key === "CREDIT_CHECK" && !approver}
														title={step.step_key === "CREDIT_CHECK" && !approver ? "An approver unblocks the credit check (a new bureau enquiry)" : undefined}
														onClick={() => act(`/steps/${step.step_key}/unblock`, { reason_code: "SPOKE_TO_APPLICANT", note: unblockNote || undefined })}
													>
														Unblock
													</Button>
												</Flex>
											))}
									</Panel>
								)}
								<Panel title="Actions">
									{view.approval_blockers.length > 0 && (
										<Box fontSize="sm" color="text.muted">
											Before approval: {view.approval_blockers.map((b) => b.toLowerCase().replace(/_/g, " ")).join(", ")}
										</Box>
									)}
									<Wrap>
										{reviewer && status === "SUBMITTED" && (
											<Button size="sm" onClick={() => act("/claim")}>
												Claim
											</Button>
										)}
										{reviewer && mine && (
											<>
												<Button size="sm" variant="outline" onClick={() => act("/release")}>
													Release
												</Button>
												<Button size="sm" onClick={() => act("/recommendation", { decision: "APPROVE" })}>
													Recommend approval
												</Button>
												<Button size="sm" variant="outline" onClick={() => act("/recommendation", { decision: "REJECT" })}>
													Recommend rejection
												</Button>
											</>
										)}
										{approver && status === "UNDER_REVIEW" && confirming !== "approve" && (
											<Button size="sm" onClick={() => setConfirming("approve")}>
												Approve
											</Button>
										)}
										{approver && (status === "UNDER_REVIEW" || status === "ON_HOLD") && confirming !== "reject" && (
											<Button size="sm" variant="outline" onClick={() => setConfirming("reject")}>
												Reject
											</Button>
										)}
										{confirming && (
											<>
												<Button
													size="sm"
													colorScheme={confirming === "reject" ? "red" : undefined}
													onClick={() => {
														setConfirming(null);
														void act(confirming === "approve" ? "/approve" : "/reject", confirming === "approve" ? {} : { reason_code: reason });
													}}
												>
													{confirming === "approve" ? "Yes, approve" : `Yes, reject (${reason.toLowerCase().replace(/_/g, " ")})`}
												</Button>
												<Button size="sm" variant="ghost" onClick={() => setConfirming(null)}>
													Cancel
												</Button>
											</>
										)}
										{status === "UNDER_REVIEW" && (
											<Button size="sm" variant="outline" onClick={() => act("/holds", { reason_code: "NEEDS_MORE_INFO" })}>
												Hold
											</Button>
										)}
										{status === "ON_HOLD" && (
											<Button size="sm" variant="outline" onClick={() => run(async () => setView((await call<CaseView>(`${base}/holds`, { method: "DELETE" })).data))}>
												Release hold
											</Button>
										)}
									</Wrap>
								</Panel>
								{reviewer && mine && (
									<Panel title="Ask for corrections">
										<CheckboxGroup value={correction.steps} onChange={(steps) => setCorrection({ ...correction, steps: steps as string[] })}>
											<Stack>
												{view.correctable_steps.map((step) => (
													<Checkbox key={step} value={step}>
														{step.toLowerCase().replace(/_/g, " ")}
													</Checkbox>
												))}
											</Stack>
										</CheckboxGroup>
										<FormControl>
											<FormLabel htmlFor="correction-message">Message to the applicant</FormLabel>
											<Textarea id="correction-message" value={correction.message} onChange={(event) => setCorrection({ ...correction, message: event.target.value })} />
										</FormControl>
										<Button size="sm" onClick={() => act("/corrections", { steps: correction.steps, reason_code: reason, message: correction.message })} isDisabled={!correction.steps.length || !correction.message.trim()}>
											Send back
										</Button>
									</Panel>
								)}
								{approver && (
									<Panel title="Show a hidden value">
										<Select size="sm" value={reveal.field} onChange={(event) => setReveal({ field: event.target.value, reason: reveal.reason })} aria-label="Value">
											<option value="PAN">PAN</option>
											<option value="ADDRESS">Full address</option>
										</Select>
										<Input size="sm" placeholder="Why you need it" value={reveal.reason} onChange={(event) => setReveal({ ...reveal, reason: event.target.value })} aria-label="Reason" />
										<Button
											size="sm"
											variant="outline"
											isDisabled={reveal.reason.trim().length < 5}
											onClick={() =>
												run(async () => {
													const reply = await call<{ value: string }>(`${base}/pii-reveal`, { body: { field: reveal.field, reason: reveal.reason } });
													setReveal({ ...reveal, value: reply.data.value });
												})
											}
										>
											Show (recorded)
										</Button>
										{reveal.value && <Text fontFamily="mono">{reveal.value}</Text>}
									</Panel>
								)}
							</Stack>
						</Grid>
					</Stack>
				);
			}}
		</StaffShell>
	);
}
