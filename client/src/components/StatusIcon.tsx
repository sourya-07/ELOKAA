import { Icon } from "@chakra-ui/react";

export type StatusTone = "ok" | "warn" | "attention" | "error" | "current" | "neutral";

/**
 * A small mark beside a status word, so a status never rests on colour alone: a tick for done or
 * verified, a clock for waiting, an exclamation for something to change, a cross for refused or blocked, an
 * arrow for "now", an open circle for later. Decorative: the word next to it is what is read out.
 */
export function StatusIcon({ tone }: { tone: StatusTone }) {
	return (
		<Icon viewBox="0 0 16 16" boxSize="0.9em" aria-hidden focusable={false} flexShrink={0} fill="none" stroke="currentColor" strokeWidth={2} strokeLinecap="round" strokeLinejoin="round" data-tone={tone}>
			{tone === "ok" && <path d="M3.5 8.5l3 3 6-7" />}
			{tone === "warn" && (
				<>
					<circle cx="8" cy="8" r="6" />
					<path d="M8 4.8V8l2 1.5" />
				</>
			)}
			{tone === "attention" && (
				<>
					<circle cx="8" cy="8" r="6" />
					<path d="M8 4.8v3.6M8 11.2h.01" />
				</>
			)}
			{tone === "error" && <path d="M4.5 4.5l7 7M11.5 4.5l-7 7" />}
			{tone === "current" && <path d="M3 8h9M8.5 4.5L12 8l-3.5 3.5" />}
			{tone === "neutral" && <circle cx="8" cy="8" r="5" />}
		</Icon>
	);
}
