import AxeBuilder from "@axe-core/playwright";
import { expect, type Page } from "@playwright/test";

/** A mobile number, PAN and voter ID nobody else used; the last digits choose the fake answers (docs/LOCAL_DEVELOPMENT.md). */
export function newApplicant(panDigits = "4321") {
	const random = (n: number) => Array.from({ length: n }, () => Math.floor(Math.random() * 10)).join("");
	const letters = (n: number) => Array.from({ length: n }, () => String.fromCharCode(65 + Math.floor(Math.random() * 26))).join("");
	return {
		mobile: `9${random(9)}`,
		email: `e2e.${Date.now()}.${random(4)}@example.com`,
		pan: `${letters(3)}P${letters(1)}${panDigits}${letters(1)}`,
		epic: `${letters(3)}${random(3)}4567`,
	};
}

/** A JPEG made in the page (each one different, so the server's same-file check never trips). */
export async function jpeg(page: Page, width = 800, height = 600): Promise<{ name: string; mimeType: string; buffer: Buffer }> {
	const base64 = await page.evaluate(
		([w, h]) => {
			const canvas = document.createElement("canvas");
			canvas.width = w;
			canvas.height = h;
			const context = canvas.getContext("2d")!;
			context.fillStyle = `hsl(${Math.floor(Math.random() * 360)}, 60%, 70%)`;
			context.fillRect(0, 0, w, h);
			context.fillStyle = "#222";
			context.font = "32px sans-serif";
			context.fillText(`${Date.now()} ${Math.random()}`, 20, 60);
			return canvas.toDataURL("image/jpeg", 0.9).split(",")[1];
		},
		[width, height],
	);
	return { name: `file-${Date.now()}.jpg`, mimeType: "image/jpeg", buffer: Buffer.from(base64, "base64") };
}

/**
 * A full-page screenshot as a reader would see it: parts below the screen rise in only when
 * scrolled to (useRevealOnScroll), so scroll through the page once first.
 */
export async function screenshotWholePage(page: Page, path: string) {
	await page.evaluate(async () => {
		// A third of the screen at a time, pausing so each part is seen arriving (a jump straight past
		// a part leaves it waiting until it is scrolled back to, as for a reader)
		for (let y = 0; y < document.documentElement.scrollHeight; y += window.innerHeight / 3) {
			window.scrollTo(0, y);
			await new Promise((resolve) => setTimeout(resolve, 100));
		}
		window.scrollTo(0, 0);
	});
	await page.waitForFunction(() => !document.querySelector("[data-reveal=wait]") && document.getAnimations().every((animation) => animation.playState !== "running"), undefined, { polling: 100, timeout: 10_000 });
	await page.screenshot({ path, fullPage: true });
}

/** No serious or critical accessibility problems on the page as it is now. */
export async function expectAccessible(page: Page) {
	// Let entrance animations finish: text caught mid-fade would read as low contrast
	// (a list that re-renders after an upload starts new ones, so wait until none is running)
	await page.waitForFunction(() => document.getAnimations().every((animation) => animation.playState !== "running"), undefined, { polling: 100 });
	await page.waitForTimeout(300);
	await page.waitForFunction(() => document.getAnimations().every((animation) => animation.playState !== "running"), undefined, { polling: 100 });
	// axe types its own copy of Playwright's Page; at run time it is this page
	const results = await new AxeBuilder({ page: page as unknown as ConstructorParameters<typeof AxeBuilder>[0]["page"] }).withTags(["wcag2a", "wcag2aa"]).analyze();
	const serious = results.violations.filter((v) => v.impact === "serious" || v.impact === "critical");
	expect(serious.map((v) => `${v.id}: ${v.nodes.map((n) => `${n.target.join(" ")} (${n.any[0]?.message ?? ""})`).join(" | ")}`)).toEqual([]);
}

/** Signs in with the fake mobile OTP and verifies the email with the fixed code, then gives the consent. */
export async function contactDetails(page: Page, applicant: ReturnType<typeof newApplicant>) {
	await page.goto("/apply");
	await page.getByLabel("Mobile number").fill(applicant.mobile);
	await page.getByRole("button", { name: "Send code" }).click();
	await page.getByLabel("Digit 1 of 6").fill("123456");
	await page.getByRole("button", { name: "Verify", exact: true }).click();
	await page.getByLabel("Email address").fill(applicant.email);
	await page.getByRole("button", { name: "Email me a code" }).click();
	await page.getByLabel("Digit 1 of 6").fill("123456");
	await page.getByRole("button", { name: "Verify email" }).click();
	await page.getByRole("checkbox").check({ force: true }); // Chakra draws its own box over the input
	await page.getByRole("button", { name: "Continue" }).click();
	await expect(page).toHaveURL(/\/apply\/identity/);
}

/** DigiLocker (the fake comes straight back), PAN and the credit check. */
export async function identityToEligibility(page: Page, applicant: ReturnType<typeof newApplicant>) {
	await page.getByRole("checkbox", { name: /fetch these details from DigiLocker/ }).check({ force: true }); // Chakra draws its own box over the input
	await page.getByRole("button", { name: "Continue to DigiLocker" }).click();
	await expect(page.getByText("XXXX-XXXX-1234")).toBeVisible();
	await page.getByRole("textbox", { name: "PAN" }).fill(applicant.pan);
	await page.getByRole("button", { name: "Verify PAN" }).click();
	await page.getByRole("checkbox", { name: /credit information/ }).check({ force: true }); // Chakra draws its own box over the input
	await page.getByRole("button", { name: "Check eligibility" }).click();
}
