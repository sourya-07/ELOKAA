import { useEffect, useState } from "react";
import { ApiError } from "@/lib/api";

/**
 * Seconds left before a code may be sent again. Always the server's number (resend_after on a send,
 * retry_after on a 429), never a guess: the server refuses an early send whatever the page shows.
 */
export function useCountdown() {
	const [wait, setWait] = useState(0);
	useEffect(() => {
		if (wait <= 0) {
			return;
		}
		const timer = setTimeout(() => setWait(wait - 1), 1000);
		return () => clearTimeout(timer);
	}, [wait]);
	/** Starts the wait from a 429 OTP_LIMIT_REACHED answer; other errors change nothing. */
	const fromError = (e: unknown) => {
		if (e instanceof ApiError && e.code === "OTP_LIMIT_REACHED" && e.problem.retry_after) {
			setWait(Math.ceil(Number(e.problem.retry_after)));
		}
	};
	return { wait, start: setWait, fromError };
}
