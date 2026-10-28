/** @type {import('next').NextConfig} */
module.exports = {
	reactStrictMode: true,
	poweredByHeader: false,
	output: "standalone",
	// Journey v2 (ADR-0007): the identity steps share one page. Old links and in-flight DigiLocker
	// returns (?state=…, passed through by Next) land there.
	async redirects() {
		const identity = ["method", "aadhaar", "pan", "address", "digilocker", "credit-check"].map((page) => ({
			source: `/apply/${page}`,
			destination: "/apply/identity",
			permanent: true,
		}));
		const documents = ["education", "id-document"].map((page) => ({
			source: `/apply/${page}`,
			destination: "/apply/documents",
			permanent: true,
		}));
		return [...identity, ...documents];
	},
	async headers() {
		return [
			{
				source: "/:path*",
				headers: [
					{ key: "X-Content-Type-Options", value: "nosniff" },
					{ key: "X-Frame-Options", value: "DENY" },
					{ key: "Referrer-Policy", value: "no-referrer" },
					{ key: "Permissions-Policy", value: "camera=(self), microphone=(), geolocation=(self)" },
					{ key: "Strict-Transport-Security", value: "max-age=31536000; includeSubDomains" },
					{ key: "Cross-Origin-Opener-Policy", value: "same-origin-allow-popups" },
				],
			},
		];
	},
};
