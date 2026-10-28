import { Box } from "@chakra-ui/react";
import { keyframes } from "@emotion/react";
import { EASE_OUT, REDUCED_MOTION } from "@/styles/motion";

const settle = keyframes`
	from { opacity: 0; transform: scale(0.9); }
	to { opacity: 1; transform: none; }
`;
const draw = keyframes`
	from { stroke-dashoffset: 1; }
	to { stroke-dashoffset: 0; }
`;
const fade = keyframes`
	from { opacity: 0; }
	to { opacity: 1; }
`;

/**
 * The tick on a good outcome (submitted, approved). Seen once or twice per applicant, so it may
 * take a moment: the circle settles, then the tick draws itself. Reduced motion: it just fades in.
 */
export function SuccessMark() {
	return (
		<Box
			as="svg"
			viewBox="0 0 48 48"
			boxSize="48px"
			aria-hidden="true"
			sx={{
				animation: `${settle} 250ms ${EASE_OUT} both`,
				"& path": { strokeDasharray: 1, animation: `${draw} 300ms ${EASE_OUT} 150ms both` },
				[REDUCED_MOTION]: { animation: `${fade} 200ms ease both`, "& path": { animation: "none", strokeDashoffset: 0 } },
			}}
		>
			<circle cx="24" cy="24" r="24" fill="var(--chakra-colors-status-ok-bg)" />
			<path
				d="M15 24.5l6 6 12-13"
				pathLength={1}
				fill="none"
				stroke="var(--chakra-colors-status-ok-fg)"
				strokeWidth={3}
				strokeLinecap="round"
				strokeLinejoin="round"
			/>
		</Box>
	);
}
