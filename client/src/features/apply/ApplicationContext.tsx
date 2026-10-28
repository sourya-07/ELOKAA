import { Center, Spinner } from "@chakra-ui/react";
import { useRouter } from "next/router";
import { createContext, type ReactNode, useCallback, useContext, useEffect, useMemo, useRef, useState } from "react";
import { Button, Stack, Text } from "@chakra-ui/react";
import { ApiError, call, type CallOptions, newKey } from "@/lib/api";
import { routeFor } from "@/lib/steps";
import type { Snapshot } from "@/lib/types";
import { delayedAppear } from "@/styles/motion";

interface ApplicationState {
	snapshot: Snapshot;
	/** Runs a write against the application with the current If-Match; the answer becomes the new snapshot. */
	write: (path: string, options?: Omit<CallOptions, "ifMatch">) => Promise<Snapshot>;
	refresh: () => Promise<Snapshot>;
	setSnapshot: (snapshot: Snapshot) => void;
}

const Context = createContext<ApplicationState | null>(null);

interface SessionState {
	/** False on a page that allows it (sign-in) while nobody is signed in */
	signedIn: boolean;
	/** Loads the application again, e.g. right after signing in */
	reload: () => void;
	/** Forgets the application in this browser (after sign-out or an ended session) */
	signOut: () => void;
}

const Session = createContext<SessionState>({ signedIn: false, reload: () => undefined, signOut: () => undefined });

export function useApplication(): ApplicationState {
	const value = useContext(Context);
	if (!value) {
		throw new Error("useApplication outside ApplicationProvider");
	}
	return value;
}

/** The application when signed in, or null (pages that also work signed out: the sign-in page). */
export function useOptionalApplication(): ApplicationState | null {
	return useContext(Context);
}

export function useApplicationSession(): SessionState {
	return useContext(Session);
}

/**
 * Loads the applicant's application (starting one after the first sign-in) and keeps the latest
 * snapshot. Signed-out visitors go back to the mobile page, unless {@code optional} (the mobile page
 * itself), where the page shows without an application. A write that loses a race (412) shows
 * the server's current version instead.
 */
export function ApplicationProvider({ children, optional = false }: { children: ReactNode; optional?: boolean }) {
	const optionalRef = useRef(optional);
	optionalRef.current = optional;
	const [signedOut, setSignedOut] = useState(false);
	const router = useRouter();
	// The Pages Router hands out a new router object on every render; keep it out of the load effect
	// so the application is loaded once, not again on every navigation
	const routerRef = useRef(router);
	routerRef.current = router;
	const [snapshot, setSnapshotState] = useState<Snapshot | null>(null);
	// The newest version, so back-to-back writes (consent, then the step) each send a current If-Match
	const latest = useRef<Snapshot | null>(null);
	// One Idempotency-Key per action on one version: a retry after a lost answer reuses it
	const keys = useRef(new Map<string, string>());
	const [failed, setFailed] = useState(false);
	const [attempt, setAttempt] = useState(0);
	// One provider stays mounted across journey pages, so an ended session must clear what it holds
	const forget = useCallback(() => {
		latest.current = null;
		keys.current.clear();
		setSnapshotState(null);
		setSignedOut(true);
	}, []);
	const setSnapshot = useCallback((next: Snapshot) => {
		const known = latest.current;
		if (known && known.application_id === next.application_id && next.version < known.version) {
			return; // an older answer (a slow read) never replaces a newer write
		}
		latest.current = next;
		setSnapshotState(next);
	}, []);

	useEffect(() => {
		let active = true;
		(async () => {
			setFailed(false);
			try {
				const reply = await call<Snapshot>("/v1/partner/applications", { method: "POST" });
				if (active) {
					setSignedOut(false);
					setSnapshot(reply.data);
				}
			} catch (error) {
				if (error instanceof ApiError && (error.status === 401 || error.status === 403)) {
					if (active) {
						forget();
					}
					if (!optionalRef.current) {
						// Keep where they were going (e.g. back from DigiLocker in another browser): after
						// signing in they continue there
						routerRef.current.replace(`/apply?next=${encodeURIComponent(routerRef.current.asPath)}`);
					}
				} else if (error instanceof ApiError && error.code === "ALREADY_APPROVED") {
					// Already a partner: show that application (the pages route to the status page)
					const approved = await call<Snapshot>("/v1/partner/applications/current").catch(() => null);
					if (active && approved) {
						setSnapshot(approved.data);
					}
				} else if (active) {
					setFailed(true);
				}
			}
		})();
		return () => {
			active = false;
		};
	}, [setSnapshot, attempt, forget]);

	const refresh = useCallback(async () => {
		const id = latest.current?.application_id;
		const reply = await call<Snapshot>(`/v1/partner/applications/${id}`);
		setSnapshot(reply.data);
		return reply.data;
	}, [setSnapshot]);

	const write = useCallback(
		async (path: string, options: Omit<CallOptions, "ifMatch"> = {}) => {
			const current = latest.current;
			if (!current) {
				throw new Error("No application loaded");
			}
			// The body is part of the action: a corrected PAN or code is a new request, not a retry
			const action = `${current.version} ${path} ${JSON.stringify(options.body ?? null)}`;
			let key: string | undefined;
			if (options.idempotent) {
				key = keys.current.get(action) ?? newKey();
				keys.current.set(action, key);
			}
			try {
				const reply = await call<Snapshot>(`/v1/partner/applications/${current.application_id}${path}`, {
					method: "POST",
					...options,
					idempotent: key ?? false,
					ifMatch: `"v${current.version}"`,
				});
				keys.current.delete(action);
				setSnapshot(reply.data);
				return reply.data;
			} catch (error) {
				if (error instanceof ApiError && error.status === 412 && error.problem.current) {
					setSnapshot(error.problem.current as Snapshot);
				}
				if (error instanceof ApiError && error.status === 401) {
					forget();
					routerRef.current.replace("/apply");
				}
				throw error;
			}
		},
		[forget, setSnapshot],
	);

	const value = useMemo(() => (snapshot ? { snapshot, write, refresh, setSnapshot } : null), [snapshot, write, refresh, setSnapshot]);
	const session = useMemo(
		() => ({ signedIn: !!snapshot, reload: () => setAttempt((n) => n + 1), signOut: forget }),
		[snapshot, forget],
	);
	if (!value && signedOut && optional) {
		return <Session.Provider value={session}>{children}</Session.Provider>;
	}
	if (!value && failed) {
		return (
			<Center minH="60vh">
				<Stack spacing={3} align="center">
					<Text>We could not open your application.</Text>
					<Button onClick={() => setAttempt((n) => n + 1)}>Try again</Button>
				</Stack>
			</Center>
		);
	}
	if (!value) {
		return (
			<Center minH="60vh" sx={delayedAppear}>
				<Spinner label="Loading your application" />
			</Center>
		);
	}
	return (
		<Session.Provider value={session}>
			<Context.Provider value={value}>{children}</Context.Provider>
		</Session.Provider>
	);
}

/** Sends the applicant to where the server says they continue, unless this page is that step or one already done. */
export function useStepGuard(allowed: (snapshot: Snapshot) => boolean) {
	const { snapshot } = useApplication();
	const router = useRouter();
	useEffect(() => {
		if (!allowed(snapshot)) {
			const target = routeFor(snapshot);
			if (target !== router.pathname) {
				router.replace(target);
			}
		}
	}, [allowed, router, snapshot]);
}
