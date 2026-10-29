import AxeBuilder from "@axe-core/playwright";
import { expect, type Locator, type Page } from "@playwright/test";

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
			// Some detail, so the file has a real photo's size (photos must be at least 20 KB, documents 10 KB)
			const noise = context.createImageData(200, 200);
			for (let i = 0; i < noise.data.length; i++) {
				noise.data[i] = i % 4 === 3 ? 255 : Math.floor(Math.random() * 256);
			}
			context.putImageData(noise, 0, 100);
			return canvas.toDataURL("image/jpeg", 0.9).split(",")[1];
		},
		[width, height],
	);
	return { name: `file-${Date.now()}.jpg`, mimeType: "image/jpeg", buffer: Buffer.from(base64, "base64") };
}

/**
 * A full-page screenshot of the whole layout: parts below the screen wait hidden until scrolled to
 * (useRevealOnScroll, and again after scrolling back up), so show them all first, as a reader sees
 * each one once it has arrived.
 */
export async function screenshotWholePage(page: Page, path: string) {
	await page.evaluate(() => document.querySelectorAll("[data-reveal=wait]").forEach((part) => part.setAttribute("data-reveal", "in")));
	await page.waitForFunction(() => document.getAnimations().every((animation) => animation.playState !== "running"), undefined, { polling: 100, timeout: 10_000 });
	await page.screenshot({ path, fullPage: true });
}

/**
 * Ticks a Chakra checkbox or radio. Chakra draws its own box over the input, so the click is forced,
 * and a forced click skips Playwright's wait for the element to stop moving: let the part finish
 * rising in (it animates each time it is scrolled to) before clicking where it is.
 */
export async function tick(box: Locator) {
	await box.scrollIntoViewIfNeeded();
	await box.page().waitForFunction(() => document.getAnimations().every((animation) => animation.playState !== "running"), undefined, { polling: 50 });
	await box.check({ force: true });
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
	await tick(page.getByRole("checkbox"));
	await page.getByRole("button", { name: "Continue" }).click();
	await expect(page).toHaveURL(/\/apply\/identity/);
}

/** DigiLocker (the fake comes straight back), PAN and the credit check. */
export async function identityToEligibility(page: Page, applicant: ReturnType<typeof newApplicant>) {
	await tick(page.getByRole("checkbox", { name: /fetch these details from DigiLocker/ }));
	await page.getByRole("button", { name: "Continue to DigiLocker" }).click();
	await expect(page.getByText("XXXX-XXXX-1234")).toBeVisible();
	await page.getByRole("textbox", { name: "PAN" }).fill(applicant.pan);
	await page.getByRole("button", { name: "Verify PAN" }).click();
	await tick(page.getByRole("checkbox", { name: /credit information/ }));
	await page.getByRole("button", { name: "Check eligibility" }).click();
}
