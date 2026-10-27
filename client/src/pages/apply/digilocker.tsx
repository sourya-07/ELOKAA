import { Button, Checkbox, Stack, Text } from "@chakra-ui/react";
import { useRouter } from "next/router";
import { useCallback, useEffect, useRef, useState } from "react";
import { ApplyShell, Section } from "@/components/ApplyShell";
import { ErrorBanner } from "@/components/ErrorBanner";
import { useApplication, useStepGuard } from "@/features/apply/ApplicationContext";
import { canOpen } from "@/features/apply/guards";
import { PanForm } from "@/features/apply/PanForm";
import { useAction } from "@/features/apply/useAction";
import { withApplication } from "@/features/apply/withApplication";
import { call } from "@/lib/api";
import { CONSENT_VERSION } from "@/lib/consent";
import { ERROR_TEXT, position } from "@/lib/steps";
import type { Snapshot } from "@/lib/types";
import { isDone } from "@/lib/types";

/** DigiLocker path, page 3: connect DigiLocker, then enter the PAN on the same page. */
function DigiLockerPage() {
	const { snapshot, write, setSnapshot } = useApplication();
	const router = useRouter();
	useStepGuard((s) => s.method === "DIGILOCKER" && (canOpen(s, "AADHAAR") || canOpen(s, "PAN")));
	const aadhaar = snapshot.steps.find((s) => s.step_key === "AADHAAR");
	const [agreed, setAgreed] = useState(false);
	const [waiting, setWaiting] = useState(false);
	const { busy, error, setError, run } = useAction();
	const completing = useRef(false);

	const complete = useCallback(
		async (state: string) => {
			const reply = await call<Snapshot>(`/v1/partner/applications/${snapshot.application_id}/digilocker-sessions/complete`, {
				body: { state },
			});
			setSnapshot(reply.data);
			const pending = reply.status === 202;
			setWaiting(pending);
			return pending;
		},
		[setSnapshot, snapshot.application_id],
	);

	// Back from DigiLocker: ?state=… finishes the trip once
	useEffect(() => {
		const state = typeof router.query.state === "string" ? router.query.state : null;
		if (state && !completing.current && !isDone(aadhaar)) {
			completing.current = true;
			// Keep the state in the address while DigiLocker is still working, so "Check again" can use it
			run(() => complete(state)).then((pending) =>
				router.replace({ pathname: "/apply/digilocker", query: pending ? { state } : {} }, undefined, { shallow: true }),
			);
		}
	}, [aadhaar, complete, router, run]);

	const connect = async () => {
		if (!agreed) {
			setError("Allow ELOKA to fetch your details from DigiLocker to continue");
			return;
		}
		await run(async () => {
			const latest = await write("/consents", { body: { purpose: "DIGILOCKER", version: CONSENT_VERSION } });
			const reply = await call<{ url: string }>(`/v1/partner/applications/${latest.application_id}/digilocker-sessions`, {
				ifMatch: `"v${latest.version}"`,
				method: "POST",
			});
			window.location.assign(reply.data.url);
		});
	};

	const summary = aadhaar?.summary as { aadhaar_masked?: string; address_from_digilocker?: boolean } | undefined;
	return (
		<ApplyShell title="Connect DigiLocker" subtitle="Your name, home address and the last 4 digits of your Aadhaar come from DigiLocker." step={position(snapshot.method, "/apply/digilocker")}>
			{!isDone(aadhaar) ? (
				<Section>
					<Text>We never see or keep your full Aadhaar number.</Text>
					{waiting ? (
						<Stack spacing={3}>
							<Text>Waiting for DigiLocker…</Text>
							<Button variant="outline" onClick={() => typeof router.query.state === "string" && run(() => complete(router.query.state as string))} isLoading={busy}>
								Check again
							</Button>
						</Stack>
					) : (
						<>
							<Checkbox isChecked={agreed} onChange={(event) => setAgreed(event.target.checked)} alignItems="flex-start">
								I allow ELOKA to fetch these details from DigiLocker for this application.
							</Checkbox>
							<Button onClick={connect} isLoading={busy}>
								Continue to DigiLocker
							</Button>
						</>
					)}
					<ErrorBanner message={aadhaar?.last_error_code ? ERROR_TEXT[aadhaar.last_error_code] ?? "DigiLocker did not finish. Please try again." : null} tone="warning" />
				</Section>
			) : (
				<>
					<Section label="Fetched from DigiLocker">
						<Text>Aadhaar: {summary?.aadhaar_masked}</Text>
						<Text color="text.muted" fontSize="sm">
							{summary?.address_from_digilocker ? "Your home address came from DigiLocker." : "DigiLocker did not give a full address. You will add it after the credit check."}
						</Text>
					</Section>
					<Section label="Your PAN">
						<PanForm askPerson={false} />
					</Section>
				</>
			)}
			<ErrorBanner message={error} />
		</ApplyShell>
	);
}

export default withApplication(DigiLockerPage);
