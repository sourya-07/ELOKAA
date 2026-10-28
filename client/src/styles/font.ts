import localFont from "next/font/local";

/**
 * Inter (DESIGN.md), served from this app: no third-party font request, CSP `font-src 'self'`
 * stays, and builds need no network. Latin subset, variable weight (OFL, fonts/OFL-Inter.txt).
 */
export const inter = localFont({
	src: "./fonts/InterVariable-latin.woff2",
	weight: "100 900",
	display: "swap",
});
