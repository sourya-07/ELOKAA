import { expect, test } from "@playwright/test";
import { expectAccessible, screenshotWholePage } from "./helpers";

// The start and sign-in pages at phone, tablet and desktop widths: nothing wider than the screen,
// no serious accessibility problems, a screenshot to look at; and the same with reduced motion.
for (const path of ["/", "/apply"]) {
	test(`${path} fits the screen`, async ({ page }, info) => {
		await page.goto(path);
		await expect(page.getByRole("heading", { level: 1 })).toBeVisible();
		const overflow = await page.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth);
		expect(overflow).toBeLessThanOrEqual(0);
		await expectAccessible(page);
		await screenshotWholePage(page, `e2e/.results/screens/layout-${info.project.name}${path.replace(/\//g, "_")}.png`);
	});
}

test("reduced motion still shows everything", async ({ page }) => {
	await page.emulateMedia({ reducedMotion: "reduce" });
	await page.goto("/");
	// Scrolled to, the way in fades in without moving
	const start = page.getByRole("link", { name: "Start your application" });
	await start.scrollIntoViewIfNeeded();
	await expect(start).toBeVisible();
	await expect(start).toHaveAttribute("data-reveal", "in");
	expect(await start.evaluate((element) => getComputedStyle(element).transform)).toBe("none");
});

test("parts below the screen wait, then rise in when scrolled to", async ({ page }, info) => {
	test.skip(info.project.name !== "phone", "one width is enough");
	await page.goto("/");
	await page.waitForTimeout(500); // the page's own entrance
	const start = page.getByRole("link", { name: "Start your application" });
	await expect(start).toHaveAttribute("data-reveal", "wait");
	expect(await start.evaluate((element) => getComputedStyle(element).opacity)).toBe("0");
	// What was on screen at load is never hidden
	await expect(page.locator("[data-reveal=wait]", { has: page.getByRole("heading", { level: 1 }) })).toHaveCount(0);
	await start.scrollIntoViewIfNeeded();
	await expect(start).toHaveAttribute("data-reveal", "in");
	await expect.poll(() => start.evaluate((element) => getComputedStyle(element).opacity)).toBe("1");
	// Back to the top: it waits again, and rises in again on the way down (every time, not once)
	await page.evaluate(() => window.scrollTo(0, 0));
	await expect(start).toHaveAttribute("data-reveal", "wait");
	await start.scrollIntoViewIfNeeded();
	await expect(start).toHaveAttribute("data-reveal", "in");
	await expect.poll(() => start.evaluate((element) => getComputedStyle(element).opacity)).toBe("1");
});
