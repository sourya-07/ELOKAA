import { defineConfig, devices } from "@playwright/test";

/**
 * End-to-end tests of the applicant journey in a real browser, against the local stack with fake
 * providers (make server-run, make client-dev; docs/TESTING.md). They prove the journey and the UI,
 * never a real provider: EPS, DigiLocker and the bureau are fakes here.
 *
 * One worker and Chromium only: the development Mac has 8 GB of memory.
 */
export default defineConfig({
	testDir: "./e2e",
	outputDir: "./e2e/.results",
	fullyParallel: false,
	workers: 1,
	retries: 0,
	timeout: 120_000,
	expect: { timeout: 15_000 },
	reporter: [["list"]],
	use: {
		baseURL: process.env.E2E_BASE_URL ?? "http://localhost:3003",
		trace: "retain-on-failure",
		// The live photo: a fake camera, and a location in Mumbai unless a test takes it away
		permissions: ["camera", "geolocation"],
		geolocation: { latitude: 19.0596, longitude: 72.8295, accuracy: 20 },
		launchOptions: { args: ["--use-fake-device-for-media-stream", "--use-fake-ui-for-media-stream"] },
	},
	projects: [
		{ name: "phone", use: { ...devices["Desktop Chrome"], viewport: { width: 360, height: 800 }, isMobile: false } },
		{ name: "tablet", use: { ...devices["Desktop Chrome"], viewport: { width: 768, height: 1024 } }, testMatch: /layout\.spec/ },
		{ name: "desktop", use: { ...devices["Desktop Chrome"], viewport: { width: 1280, height: 900 } }, testMatch: /layout\.spec/ },
	],
});
