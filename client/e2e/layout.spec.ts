import { expect, test } from "@playwright/test";
import { expectAccessible } from "./helpers";

// The start and sign-in pages at phone, tablet and desktop widths: nothing wider than the screen,
// no serious accessibility problems, a screenshot to look at; and the same with reduced motion.
for (const path of ["/", "/apply"]) {
	test(`${path} fits the screen`, async ({ page }, info) => {
		await page.goto(path);
		await expect(page.getByRole("heading", { level: 1 })).toBeVisible();
		const overflow = await page.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth);
		expect(overflow).toBeLessThanOrEqual(0);
		await expectAccessible(page);
		await page.screenshot({ path: `e2e/.results/screens/layout-${info.project.name}${path.replace(/\//g, "_")}.png`, fullPage: true });
	});
}

test("reduced motion still shows everything", async ({ page }) => {
	await page.emulateMedia({ reducedMotion: "reduce" });
	await page.goto("/");
	await expect(page.getByRole("link", { name: "Start your application" }).first()).toBeVisible();
});
