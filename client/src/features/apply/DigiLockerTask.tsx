import { Button, Checkbox, Link, Stack, Text } from "@chakra-ui/react";
import { useRouter } from "next/router";
import { useCallback, useEffect, useRef, useState } from "react";
import { ErrorBanner } from "@/components/ErrorBanner";
import { ApiError, call } from "@/lib/api";
import { CONSENT_VERSION } from "@/lib/consent";
import { ERROR_TEXT } from "@/lib/steps";
import type { Snapshot } from "@/lib/types";
import { delayedAppear } from "@/styles/motion";
import { useApplication } from "./ApplicationContext";
import { useAction } from "./useAction";

/** Seconds between automatic checks after coming back from DigiLocker (about a minute and a half in all). */
const BACKOFF = [2, 3, 5, 8, 13, 21, 34];

/** DigiLocker's official sign-up page (checked on digilocker.gov.in, docs/OPEN_QUESTIONS.md C-6). */
const DIGILOCKER = {
	signup: "https://www.digilocker.gov.in/web/signup",
};

/**
 * Aadhaar through DigiLocker: what will happen, the consent, the trip to DigiLocker (same tab), and
 * the way back. Back here with `?state=`, the trip is finished once; while DigiLocker is still
 * working the page checks again by itself for a while, then offers "Check again".
 */
export function DigiLockerTask() {
	const { snapshot, write, setSnapshot } = useApplication();
	const router = useRouter();
	const step = snapshot.steps.find((s) => s.step_key === "AADHAAR");
	const state = typeof router.query.state === "string" ? router.query.state : null;
	const [agreed, setAgreed] = useState(false);
	const [waiting, setWaiting] = useState(false);
	const [tries, setTries] = useState(0);
	const [linkProblem, setLinkProblem] = useState<string | null>(null);
	const { busy, error, setError, run } = useAction();
	const started = useRef(false);

	const dropState = useCallback(() => {
		router.replace({ pathname: "/apply/identity", query: {} }, undefined, { shallow: true });
	}, [router]);

	const complete = useCallback(
		async (token: string) => {
			try {
				const reply = await call<Snapshot>(`/v1/partner/applications/${snapshot.application_id}/digilocker-sessions/complete`, {
					body: { state: token },
				});
				setSnapshot(reply.data);
				const pending = reply.status === 202;
				setWaiting(pending);
				if (!pending) {
					dropState();
				}
			} catch (e) {
				if (e instanceof ApiError && ["DIGILOCKER_ALREADY_USED", "DIGILOCKER_EXPIRED", "VALIDATION_FAILED"].includes(e.code)) {
					setWaiting(false);
					setLinkProblem("This DigiLocker link has ended. If your Aadhaar is not shown as done, start again.");
					dropState();
					return;
				}
				throw e;
			}
		},
		[dropState, setSnapshot, snapshot.application_id],
	);

	// Back from DigiLocker: finish the trip once
	useEffect(() => {
		if (state && !started.current) {
			started.current = true;
			void run(() => complete(state));
		}
	}, [complete, run, state]);

	// Still working at DigiLocker: check again by itself, slower each time
	useEffect(() => {
		if (!waiting || !state || tries >= BACKOFF.length) {
			return;
		}
		const timer = setTimeout(() => {
			setTries((n) => n + 1);
			void run(() => complete(state));
		}, BACKOFF[tries] * 1000);
		return () => clearTimeout(timer);
	}, [complete, run, state, tries, waiting]);

	const start = async () => {
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

	const blocked = step?.status === "BLOCKED";
	const attemptsLeft = (step?.max_attempts ?? 0) - (step?.attempt_count ?? 0);
	const stepError = step?.last_error_code ? ERROR_TEXT[step.last_error_code] ?? "DigiLocker did not finish. Please try again." : null;
	const awayElsewhere = step?.status === "PENDING_EXTERNAL" && !state;

	if (blocked) {
		return <ErrorBanner tone="error" message={`${stepError ?? "Too many attempts."} Our team will contact you.`} />;
	}

	if (state && (waiting || busy)) {
		return (
			<Stack spacing={3} role="status" aria-live="polite">
				<Text sx={delayedAppear}>Checking with DigiLocker…</Text>
				{step?.last_error_code === "DIGILOCKER_UNAVAILABLE" && <Text color="text.muted">{ERROR_TEXT.DIGILOCKER_UNAVAILABLE}</Text>}
				{waiting && tries >= BACKOFF.length && (
					<Stack direction={{ base: "column", sm: "row" }} spacing={3}>
						<Button variant="outline" onClick={() => run(() => complete(state))} isLoading={busy}>
							Check again
						</Button>
						<Button variant="link" onClick={() => setWaiting(false)}>
							Start again
						</Button>
					</Stack>
				)}
				<ErrorBanner message={error} />
			</Stack>
		);
	}


	return (
		<Stack spacing={4}>
			<Text>You will go to DigiLocker, the government&apos;s document wallet, and sign in with the mobile number linked to your Aadhaar. There you allow ELOKA to read your Aadhaar. Then you come back here.</Text>
			<Text color="text.muted" fontSize="sm">
				We get your name, date of birth, home address and the last 4 digits of your Aadhaar. We never see or keep your full Aadhaar number from DigiLocker.
			</Text>
			{awayElsewhere && (
				<Text role="status">
					We are waiting for DigiLocker. If you finished it in another browser or the DigiLocker app opened a different one, sign in there with your mobile number to finish, or start again here.
				</Text>
			)}
			<ErrorBanner message={linkProblem ?? (stepError && step?.status !== "COMPLETED" ? `${stepError}${step?.status === "FAILED" && step.last_error_code !== "DIGILOCKER_UNAVAILABLE" ? ` ${attemptsLeft} attempts left.` : ""}` : null)} tone="warning" />
			<Checkbox isChecked={agreed} onChange={(event) => setAgreed(event.target.checked)} alignItems="flex-start">
				I allow ELOKA to fetch these details from DigiLocker for this application.
			</Checkbox>
			<ErrorBanner message={error} />
			<Stack direction={{ base: "column", sm: "row" }} spacing={3} align={{ sm: "center" }}>
				<Button onClick={start} isLoading={busy}>
					{awayElsewhere || stepError || linkProblem ? "Start again" : "Continue to DigiLocker"}
				</Button>
				{/* Straight to DigiLocker's own sign-up, in a new tab so this application stays open */}
				<Link href={DIGILOCKER.signup} isExternal color="text.link" fontWeight={500}>
					Don&apos;t have a DigiLocker account? Sign up on DigiLocker
				</Link>
			</Stack>
		</Stack>
	);
}
