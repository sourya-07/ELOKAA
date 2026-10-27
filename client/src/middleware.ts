import { NextResponse, type NextRequest } from "next/server";

/**
 * Content Security Policy with a fresh nonce per page: only scripts carrying the nonce run, so an
 * injected script does not. Chakra writes inline styles, so styles allow 'unsafe-inline'. Google's
 * sign-in script is allowed on the staff pages only; applicant pages load nothing from elsewhere.
 */
export function middleware(request: NextRequest) {
	const nonce = btoa(crypto.randomUUID());
	const staff = request.nextUrl.pathname.startsWith("/staff");
	const dev = process.env.NODE_ENV !== "production";
	const csp = [
		"default-src 'self'",
		`script-src 'self' 'nonce-${nonce}' 'strict-dynamic'${staff ? " https://accounts.google.com" : ""}${dev ? " 'unsafe-eval'" : ""}`,
		"style-src 'self' 'unsafe-inline'" + (staff ? " https://accounts.google.com" : ""),
		"img-src 'self' blob: data:",
		"media-src 'self' blob:",
		"font-src 'self'",
		"connect-src 'self'" + (staff ? " https://accounts.google.com" : ""),
		`frame-src ${staff ? "https://accounts.google.com" : "'none'"}`,
		"frame-ancestors 'none'",
		"form-action 'self'",
		"base-uri 'none'",
		"object-src 'none'",
	].join("; ");
	const headers = new Headers(request.headers);
	headers.set("x-nonce", nonce);
	const response = NextResponse.next({ request: { headers } });
	response.headers.set("Content-Security-Policy", csp);
	return response;
}

export const config = {
	// Pages only: not the API proxy, static files or images
	matcher: [{ source: "/((?!api|_next/static|_next/image|favicon.ico).*)" }],
};
