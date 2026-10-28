import { extendTheme, type ThemeConfig } from "@chakra-ui/react";
import { inter } from "./font";

/**
 * ELOKA platform design system (eloka_design/design-system-and-prompts/DESIGN.md): warm paper
 * canvas, ink text and primary actions, Connect Blue for links and focus, hairline borders,
 * Inter, 8px controls and 16px cards, sentence case.
 */
const config: ThemeConfig = { initialColorMode: "light", useSystemColorMode: false };

export const theme = extendTheme({
	config,
	fonts: {
		heading: `${inter.style.fontFamily}, ui-sans-serif, system-ui, -apple-system, "Segoe UI", sans-serif`,
		body: `${inter.style.fontFamily}, ui-sans-serif, system-ui, -apple-system, "Segoe UI", sans-serif`,
	},
	colors: {
		ink: { 900: "#0A0A0A", 700: "#2F312F", 500: "#5C5C57", 300: "#85857F" },
		paper: { 50: "#FFFFFF", 100: "#FAFAF7", 200: "#F4F3EE", 300: "#E5E5E0" },
		connect: { 50: "#E8EBFA", 500: "#11299E", 600: "#0D2080" },
	},
	semanticTokens: {
		colors: {
			"bg.canvas": "paper.100",
			"bg.surface": "paper.50",
			"bg.subtle": "paper.200",
			"border.hairline": "paper.300",
			"text.primary": "ink.900",
			"text.muted": "ink.500",
			"text.link": "connect.500",
			"status.ok.bg": "#E7F4EC",
			"status.ok.fg": "#1D6B3A",
			"status.warn.bg": "#FFF7E6",
			"status.warn.fg": "#8A5300",
			"status.error.bg": "#FDECEA",
			"status.error.fg": "#93000A",
		},
	},
	radii: { control: "8px", card: "16px" },
	styles: {
		global: {
			body: { bg: "bg.canvas", color: "text.primary", fontSize: "16px" },
			"*:focus-visible": { outline: "2px solid #11299E", outlineOffset: "2px" },
		},
	},
	components: {
		Button: {
			baseStyle: { borderRadius: "control", fontWeight: 600 },
			variants: {
				solid: { bg: "ink.900", color: "white", _hover: { bg: "ink.700", _disabled: { bg: "ink.900" } } },
				outline: { borderColor: "ink.300", color: "ink.900", bg: "bg.surface" },
				link: { color: "text.link" },
			},
			defaultProps: { variant: "solid", size: "lg" },
		},
		Input: {
			variants: {
				outline: { field: { bg: "bg.surface", borderColor: "ink.300", borderRadius: "control" } },
			},
			defaultProps: { size: "lg" },
		},
		Select: {
			variants: { outline: { field: { bg: "bg.surface", borderColor: "ink.300", borderRadius: "control" } } },
			defaultProps: { size: "lg" },
		},
		FormLabel: { baseStyle: { fontWeight: 500, fontSize: "sm" } },
	},
});
