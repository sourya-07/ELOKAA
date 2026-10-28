import type { SystemStyleObject } from "@chakra-ui/react";
import { keyframes } from "@emotion/react";

/**
 * Motion for the applicant journey (DESIGN.md: restrained, motion only explains a change of state).
 * Only transform and opacity move. Under prefers-reduced-motion everything keeps its fade and
 * loses its movement. The staff console stays still: it is used all day.
 */

/** Strong ease-out: things entering or answering a press. */
export const EASE_OUT = "cubic-bezier(0.23, 1, 0.32, 1)";
/** Strong ease-in-out: something moving on screen (the progress line). */
export const EASE_IN_OUT = "cubic-bezier(0.77, 0, 0.175, 1)";

const rise = keyframes`
	from { opacity: 0; transform: translateY(8px); }
	to { opacity: 1; transform: none; }
`;
const fade = keyframes`
	from { opacity: 0; }
	to { opacity: 1; }
`;

export const REDUCED_MOTION = "@media (prefers-reduced-motion: reduce)";

/** Something new appearing in place: a page's sections, an error, an uploaded file. */
export function enter(delayMs = 0, durationMs = 300): SystemStyleObject {
	return {
		animation: `${rise} ${durationMs}ms ${EASE_OUT} ${delayMs}ms both`,
		[REDUCED_MOTION]: { animation: `${fade} 200ms ease ${delayMs}ms both` },
	};
}

/**
 * The direct children of a container enter one after another, 50 ms apart (the first six; the
 * rest with the sixth). Decorative only: nothing waits for it.
 */
export function staggerChildren(): SystemStyleObject {
	const style: SystemStyleObject = { "& > *": enter() };
	for (let n = 2; n <= 6; n++) {
		style[`& > *:nth-child(${n})`] = { animationDelay: `${(n - 1) * 50}ms` };
	}
	style["& > *:nth-child(n+7)"] = { animationDelay: "250ms" };
	return style;
}

/**
 * A loading indicator that only shows when loading takes a while: a fast answer never flashes a
 * spinner on screen.
 */
export const delayedAppear: SystemStyleObject = {
	animation: `${fade} 200ms ${EASE_OUT} 400ms both`,
};

const PRESSED = "& .chakra-button:not(:disabled):active";

/**
 * Feedback inside the applicant journey, set on its frame (ApplyShell) so the staff console, which
 * uses the same buttons and banners, stays still: buttons give a little under the finger, and an
 * error or notice fades in instead of pushing the form down in one jump.
 */
export const journeyFeedback: SystemStyleObject = {
	"& [role=alert]": enter(0, 200),
	// A status that changes (waiting → verified) fades in where it is: opacity only, 150 ms
	"& .status-pill": { animation: `${fade} 150ms ${EASE_OUT} both` },
	// A photo just taken or a preview appears softly instead of popping in
	"& .camera-preview": { animation: `${fade} 200ms ${EASE_OUT} both` },
	"& .chakra-button": {
		transitionProperty: "background-color, border-color, color, transform",
		transitionDuration: "160ms",
		transitionTimingFunction: EASE_OUT,
	},
	[PRESSED]: { transform: "scale(0.97)" },
	// Same selector, so it wins by coming later
	[REDUCED_MOTION]: { [PRESSED]: { transform: "none" }, "& .status-pill, & .camera-preview": { animation: "none" } },
};

/** Reads the reduced-motion setting for animations started from script. */
export function prefersReducedMotion(): boolean {
	return typeof window !== "undefined" && window.matchMedia?.("(prefers-reduced-motion: reduce)").matches === true;
}
