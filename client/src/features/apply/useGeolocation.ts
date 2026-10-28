import { useCallback, useEffect, useRef, useState } from "react";

export interface Fix {
	latitude: number;
	longitude: number;
	/** metres, as the device reports it */
	accuracy: number;
	/** when the device took it */
	at: number;
}

export type LocationState =
	| { kind: "idle" }
	| { kind: "locating" }
	| { kind: "ready"; fix: Fix }
	| { kind: "denied" }
	| { kind: "unavailable" }
	| { kind: "timeout" }
	| { kind: "unsupported" };

/** A fix older than this is not "where the photo is taken". */
export const MAX_AGE_MS = 2 * 60 * 1000;
/** Rougher than this the server refuses the photo (eloka.onboarding.location.max-accuracy-m). */
export const MAX_ACCURACY_M = 1000;
/** Rougher than this the photo is accepted but staff see it flagged. */
export const GOOD_ACCURACY_M = 100;

/**
 * The device's location for the live photo. It starts only when asked (from a button press, so
 * the browser's permission prompt makes sense), keeps watching while the camera is open, and keeps
 * the most precise recent fix. It never invents a position: no fix means the photo cannot be sent.
 */
export function useGeolocation() {
	const [state, setState] = useState<LocationState>({ kind: "idle" });
	const watch = useRef<number | null>(null);
	const best = useRef<Fix | null>(null);

	const stop = useCallback(() => {
		if (watch.current !== null && typeof navigator !== "undefined" && navigator.geolocation) {
			navigator.geolocation.clearWatch(watch.current);
		}
		watch.current = null;
	}, []);

	useEffect(() => stop, [stop]);

	const start = useCallback(() => {
		if (typeof navigator === "undefined" || !navigator.geolocation || (typeof window !== "undefined" && window.isSecureContext === false)) {
			setState({ kind: "unsupported" });
			return;
		}
		stop();
		best.current = null;
		setState({ kind: "locating" });
		watch.current = navigator.geolocation.watchPosition(
			(position) => {
				const fix: Fix = {
					latitude: position.coords.latitude,
					longitude: position.coords.longitude,
					accuracy: position.coords.accuracy,
					at: position.timestamp || Date.now(),
				};
				const current = best.current;
				// A more precise fix wins; an old one is replaced by any new one
				if (!current || fix.accuracy <= current.accuracy || Date.now() - current.at > MAX_AGE_MS) {
					best.current = fix;
					setState({ kind: "ready", fix });
				}
			},
			(error) => {
				if (best.current) {
					return; // keep the fix we have; a later error does not undo it
				}
				stop();
				setState({ kind: error.code === 1 ? "denied" : error.code === 3 ? "timeout" : "unavailable" });
			},
			{ enableHighAccuracy: true, timeout: 20000, maximumAge: 0 },
		);
	}, [stop]);

	/** The fix to send with a photo taken now, or null when there is none recent enough. */
	const current = useCallback((): Fix | null => {
		const fix = best.current;
		return fix && Date.now() - fix.at <= MAX_AGE_MS ? fix : null;
	}, []);

	return { state, start, stop, current };
}
