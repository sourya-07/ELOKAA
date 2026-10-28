/** Seconds as m:ss, so a 15-minute wait reads "15:00", not "0:900". */
export function clock(seconds: number): string {
	const whole = Math.max(0, Math.floor(seconds));
	return `${Math.floor(whole / 60)}:${String(whole % 60).padStart(2, "0")}`;
}
