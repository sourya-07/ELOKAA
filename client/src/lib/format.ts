/** Seconds as m:ss, so a 15-minute wait reads "15:00", not "0:900". */
export function clock(seconds: number): string {
	const whole = Math.max(0, Math.floor(seconds));
	return `${Math.floor(whole / 60)}:${String(whole % 60).padStart(2, "0")}`;
}

/**
 * A wait the server set (resend_after, retry_after) in words: "1 min" from a minute up (rounded up, so
 * it never promises less than the server allows), seconds below that.
 */
export function waitText(seconds: number): string {
	const whole = Math.max(0, Math.ceil(seconds));
	return whole >= 60 ? `${Math.ceil(whole / 60)} min` : `${whole} s`;
}
