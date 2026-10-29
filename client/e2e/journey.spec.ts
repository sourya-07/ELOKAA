import { expect, test } from "@playwright/test";
import { contactDetails, expectAccessible, identityToEligibility, jpeg, newApplicant, screenshotWholePage, tick } from "./helpers";

test("an eligible applicant goes from the start page to a submitted application", async ({ page }) => {
	const applicant = newApplicant("4321");
	await page.goto("/");
	await expect(page.getByText("Your CIBIL credit score is 700 or more.")).toBeVisible();
	await expect(page.getByText("Keep your documents ready in digital format (soft copies).")).toBeVisible();
	// seven languages; only English (UK) can be chosen until a reviewed translation exists
	const languages = page.getByLabel("Language").locator("option");
	await expect(languages).toHaveCount(7);
	await expect(page.getByLabel("Language").locator("option:not([disabled])")).toHaveText(["English (UK)"]);
	await expectAccessible(page);
	await screenshotWholePage(page, "e2e/.results/screens/01-start.png");

	await contactDetails(page, applicant);
	await expectAccessible(page);
	await identityToEligibility(page, applicant);

	await tick(page.getByRole("radio", { name: "Voter ID" }));
	await page.getByLabel("Voter ID (EPIC) number").fill(applicant.epic);
	await page.getByRole("button", { name: "Verify", exact: true }).click();
	// verified results are read-only: no "Change" on Aadhaar, PAN or the voter ID
	await expect(page.getByRole("button", { name: "Change" })).toHaveCount(0);
	await expectAccessible(page);
	await screenshotWholePage(page, "e2e/.results/screens/02-identity.png");
	await page.getByRole("button", { name: "Continue" }).click();

	// Photos: the live one with the camera and the location, then the passport one
	await expect(page).toHaveURL(/\/apply\/photos/);
	await page.getByRole("button", { name: "Open camera" }).click();
	await page.getByRole("button", { name: "Take photo" }).click();
	await expect(page.getByText(/Location found/)).toBeVisible();
	await page.getByRole("button", { name: "Use photo" }).click();
	await expect(page.getByText(/Photo taken · location within about/)).toBeVisible();
	await expect(page.getByText(/^Latitude -?\d+\.\d{5}, longitude -?\d+\.\d{5}$/)).toBeVisible();
	await page.locator("input[type=file]:not([capture])").setInputFiles(await jpeg(page, 600, 750));
	await expect(page.getByText("Uploaded")).toBeVisible();
	await expectAccessible(page);
	await screenshotWholePage(page, "e2e/.results/screens/03-photos.png");
	await page.getByRole("button", { name: "Save and continue" }).click();

	// Documents: identity and education
	await expect(page).toHaveURL(/\/apply\/documents/);
	const slots = ["Aadhaar: front", "Aadhaar: back", "PAN card", "Voter ID: front", "Voter ID: back", "Class 10 marksheet", "Class 12 marksheet", "Graduation certificate"];
	for (const label of slots) {
		const slot = page.locator("div").filter({ has: page.getByText(label, { exact: true }) }).filter({ has: page.locator("input[type=file]") }).last();
		await slot.locator("input[type=file]").setInputFiles(await jpeg(page));
		await expect(slot.getByText("Uploaded")).toBeVisible();
	}
	await expectAccessible(page);
	await screenshotWholePage(page, "e2e/.results/screens/04-documents.png");
	await page.getByRole("button", { name: "Save and continue" }).click();

	// Review and submit
	await expect(page).toHaveURL(/\/apply\/review/);
	await expect(page.getByText("Still to do before you can submit")).toBeHidden();
	await expect(page.getByText(/Mobile \+91 \*{6}\d{4}/)).toBeVisible();
	await expectAccessible(page);
	await tick(page.getByRole("checkbox", { name: /information I have given is true/ }));
	await page.getByRole("button", { name: "Submit application" }).click();
	await expect(page.getByText("Submit your application?")).toBeVisible();
	await page.screenshot({ path: "e2e/.results/screens/05-confirm.png" });
	await page.getByRole("button", { name: "Submit", exact: true }).click();
	await expect(page.getByRole("heading", { name: "Application submitted" })).toBeVisible();
	await expect(page.getByText("What happens next")).toBeVisible();
	await expect(page.getByText(/^[1-5]\d{9}$/)).toBeVisible(); // the 10-digit application number
	await expect(page.getByText(/^\+91 \*{6}\d{4}$/)).toBeVisible();
	expect(await page.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth)).toBeLessThanOrEqual(0);
	await expectAccessible(page);
	await screenshotWholePage(page, "e2e/.results/screens/06-submitted.png");
});

test("a CIBIL score below 700 ends the application respectfully", async ({ page }) => {
	const applicant = newApplicant("6990"); // the fake bureau answers 699
	await contactDetails(page, applicant);
	await identityToEligibility(page, applicant);
	await expect(page.getByRole("heading", { name: "You are not eligible right now" })).toBeVisible();
	await expect(page.getByText(/699/)).toHaveCount(0); // the score is never shown
	await expectAccessible(page);
});

test("without the location the live photo cannot be sent", async ({ browser }) => {
	const context = await browser.newContext({ permissions: ["camera"], viewport: { width: 360, height: 800 } });
	const page = await context.newPage();
	const applicant = newApplicant("4321");
	await contactDetails(page, applicant);
	await identityToEligibility(page, applicant);
	await page.getByLabel("Voter ID (EPIC) number").fill(applicant.epic);
	await page.getByRole("button", { name: "Verify", exact: true }).click();
	await page.getByRole("button", { name: "Continue" }).click();
	await page.getByRole("button", { name: "Open camera" }).click();
	await page.getByRole("button", { name: "Take photo" }).click();
	await expect(page.getByText(/Location is blocked for this site/)).toBeVisible();
	await expect(page.getByRole("button", { name: "Use photo" })).toBeDisabled();
	await context.close();
});
