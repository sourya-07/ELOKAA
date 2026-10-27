import { dirname } from "node:path";
import { fileURLToPath } from "node:url";
import { FlatCompat } from "@eslint/eslintrc";

const compat = new FlatCompat({ baseDirectory: dirname(fileURLToPath(import.meta.url)) });

const config = [
	{ ignores: [".next/**", "node_modules/**", "coverage/**", "next-env.d.ts"] },
	...compat.extends("next/core-web-vitals", "next/typescript"),
	{
		// Node config files (CommonJS) read by Next.js and Jest
		files: ["*.config.js"],
		rules: { "@typescript-eslint/no-require-imports": "off" },
	},
	{
		rules: {
			// Personal data must never reach the browser console (docs/SECURITY.md)
			"no-console": ["error", { allow: ["warn", "error"] }],
		},
	},
];

export default config;
