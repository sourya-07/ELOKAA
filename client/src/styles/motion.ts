import type { SystemStyleObject } from "@chakra-ui/react";
import { keyframes } from "@emotion/react";
import { useEffect, useRef, useState } from "react";

/**
 * Motion for the applicant journey (DESIGN.md: restrained, motion only explains a change of state).
 * Only transform and opacity move. Under prefers-reduced-motion everything keeps its fade and
 * loses its movement. The staff console stays still: it is used all day.
 */

/** Strong ease-out: things entering or answering a press. */
export const EASE_OUT = "cubic-bezier(0.23, 1, 0.32, 1)";
/** Strong ease-in-out: something moving on screen (the progress line). */
export const EASE_IN_OUT = "cubic-bezier(0.77, 0, 0.175, 1)";

/** The same curves for framer-motion (the progress line; Chakra brings framer-motion 11). */
export const EASE_OUT_CURVE = [0.23, 1, 0.32, 1] as const;
export const EASE_IN_OUT_CURVE = [0.77, 0, 0.175, 1] as const;

const rise = keyframes`
	from { opacity: 0; transform: translateY(8px); }
	to { opacity: 1; transform: none; }
`;
const fade = keyframes`
	from { opacity: 0; }
	to { opacity: 1; }
`;
const pop = keyframes`
	from { opacity: 0; transform: scale(0.92); }
	to { opacity: 1; transform: none; }
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
	// Last, so a child that was waiting below the screen rises without the stagger's delay
	return { ...style, ...revealChildren };
}

/**
 * The two states `useRevealOnScroll` gives a child: waiting below the screen (hidden, still in the
 * layout and readable by screen readers) and revealed (rises in, or fades under reduced motion; it
 * starts again each time a child goes from waiting to revealed).
 * `staggerChildren()` includes it; put it on any other container that uses the hook.
 */
export const revealChildren: SystemStyleObject = {
	"& > [data-reveal=wait]": { opacity: 0, animation: "none" },
	"& > [data-reveal=in]": enter(0, 300),
	"@media print": { "& > [data-reveal=wait]": { opacity: 1 } },
};

/**
 * Long pages: children below the screen wait, hidden, and rise in when scrolled to (40 px above the
 * bottom edge). It happens every time: a child that leaves through the bottom of the screen (the
 * reader scrolled back up past it) waits again and rises in again on the way down. Children that
 * come back in from the top stay still. Nothing runs while scrolling: two IntersectionObservers say
 * when a child arrives or has left, and the motion is transform and opacity only, so scrolling never
 * lags. What is on screen when the page loads keeps the page's own entrance. Children added later (a
 * task that appears, a list that loads) are looked at after each render. Without
 * IntersectionObserver everything simply shows. Pair with `staggerChildren()` or `revealChildren`.
 */
export function useRevealOnScroll<T extends HTMLElement>() {
	const ref = useRef<T>(null);
	const observers = useRef<{ arrive: IntersectionObserver; leave: IntersectionObserver } | null>(null);
	const seen = useRef(new WeakSet<Element>());

	useEffect(() => {
		if (typeof IntersectionObserver === "undefined") {
			return;
		}
		// A fresh start (React may mount twice in development): every child is looked at again
		seen.current = new WeakSet();
		const arrive = new IntersectionObserver(
			(entries) => {
				for (const entry of entries) {
					if (entry.isIntersecting && entry.target.getAttribute("data-reveal") === "wait") {
						entry.target.setAttribute("data-reveal", "in");
					}
				}
			},
			// A fixed 40 px, not a share of the window: on a tall screen a share could keep the last
			// part of a page (above only its bottom padding) from ever arriving
			{ rootMargin: "0px 0px -40px 0px" },
		);
		// Waits again only once wholly below the screen, so nothing visible ever blinks out
		const leave = new IntersectionObserver((entries) => {
			for (const entry of entries) {
				if (!entry.isIntersecting && entry.boundingClientRect.top >= (entry.rootBounds?.bottom ?? window.innerHeight)) {
					entry.target.setAttribute("data-reveal", "wait");
				}
			}
		});
		observers.current = { arrive, leave };
		return () => {
			arrive.disconnect();
			leave.disconnect();
		};
	}, []);

	useEffect(() => {
		const container = ref.current;
		const watchers = observers.current;
		if (!container || !watchers) {
			return;
		}
		for (const child of Array.from(container.children)) {
			if (seen.current.has(child)) {
				continue;
			}
			seen.current.add(child);
			// + 8 px: the page's entrance may still hold the child 8 px low; a sliver on screen stays
			if (child.getBoundingClientRect().top > window.innerHeight + 8) {
				child.setAttribute("data-reveal", "wait");
			} else if (child.getAttribute("data-reveal") === "wait") {
				// Marked on an earlier pass (a development double mount) but on screen now: never left hidden
				child.removeAttribute("data-reveal");
			}
			// Every child, including those on screen at load: any of them can leave and come back
			watchers.arrive.observe(child);
			watchers.leave.observe(child);
		}
	});

	return ref;
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
	// A status that has just turned to done or verified grows in a little (after the fade above, so it wins)
	"& [data-turned=ok]": { animation: `${pop} 200ms ${EASE_OUT} both` },
	// A photo just taken or a preview appears softly instead of popping in
	"& .camera-preview": { animation: `${fade} 200ms ${EASE_OUT} both` },
	"& .chakra-button": {
		transitionProperty: "background-color, border-color, color, transform",
		transitionDuration: "160ms",
		transitionTimingFunction: EASE_OUT,
	},
	[PRESSED]: { transform: "scale(0.97)" },
	// Same selector, so it wins by coming later
	[REDUCED_MOTION]: {
		[PRESSED]: { transform: "none" },
		"& .status-pill, & .camera-preview": { animation: "none" },
		"& [data-turned=ok]": { animation: `${fade} 150ms ease both` },
	},
};

/** Reads the reduced-motion setting for animations started from script. */
export function prefersReducedMotion(): boolean {
	return typeof window !== "undefined" && window.matchMedia?.("(prefers-reduced-motion: reduce)").matches === true;
}

/**
 * True from the render where `now` turns true until it turns false again; false on the first
 * render, so a page that loads with something already done or open does not animate it. Drives
 * one-off motion such as a status turning to done or a task panel opening.
 */
export function useTurnedOn(now: boolean): boolean {
	const [before, setBefore] = useState(now);
	const [turned, setTurned] = useState(false);
	if (now !== before) {
		setBefore(now);
		setTurned(now);
	}
	return turned;
}
