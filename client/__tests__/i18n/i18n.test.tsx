import { fireEvent, screen, within } from "@testing-library/react";
import { readFileSync } from "fs";
import { join } from "path";
import { LanguageSelector } from "@/components/LanguageSelector";
import { coverage, isAvailable, translate, usableLocale } from "@/i18n/catalogs";
import { I18nProvider, localeFromCookie } from "@/i18n/I18nProvider";
import { LOCALES } from "@/i18n/locales";
import { render } from "../render";

describe("language selector", () => {
	it("lists exactly the seven languages, in order, each once", () => {
		render(
			<I18nProvider>
				<LanguageSelector />
			</I18nProvider>,
		);
		const options = within(screen.getByLabelText("Language")).getAllByRole("option");
		expect(options.map((o) => o.getAttribute("value"))).toEqual(["pa-IN", "en-GB", "hi-IN", "ur-IN", "gu-IN", "bn-IN", "ta-IN"]);
		expect(new Set(options.map((o) => o.textContent)).size).toBe(7);
		expect(options[1]).toHaveTextContent("English (UK)");
		expect(options[0]).toHaveTextContent("ਪੰਜਾਬੀ (Punjabi)");
	});

	it("offers only languages with a complete, reviewed translation, and says so for the rest", () => {
		render(
			<I18nProvider>
				<LanguageSelector />
			</I18nProvider>,
		);
		const select = screen.getByLabelText("Language") as HTMLSelectElement;
		const options = within(select).getAllByRole("option") as HTMLOptionElement[];
		expect(options.filter((o) => !o.disabled).map((o) => o.value)).toEqual(["en-GB"]);
		expect(options[2]).toHaveTextContent("हिन्दी (Hindi): not available yet");
		fireEvent.change(select, { target: { value: "hi-IN" } });
		expect(select.value).toBe("en-GB");
		expect(document.cookie).not.toContain("eloka_lang=hi-IN");
	});

	it("keeps the choice in a cookie and on the page's language", () => {
		render(
			<I18nProvider>
				<LanguageSelector />
			</I18nProvider>,
		);
		fireEvent.change(screen.getByLabelText("Language"), { target: { value: "en-GB" } });
		expect(document.cookie).toContain("eloka_lang=en-GB");
		expect(document.documentElement.lang).toBe("en-GB");
		expect(document.documentElement.dir).toBe("ltr");
	});
});

describe("catalogs", () => {
	it("has every key in English and no other language yet", () => {
		expect(coverage("en-GB")).toBe(1);
		for (const locale of LOCALES.filter((l) => l.code !== "en-GB")) {
			expect(isAvailable(locale.code)).toBe(false);
		}
	});

	it("falls back to English for a language that cannot be used, and fills placeholders", () => {
		expect(usableLocale("ta-IN")).toBe("en-GB");
		expect(localeFromCookie("a=b; eloka_lang=xx-YY")).toBe("en-GB");
		expect(localeFromCookie(undefined)).toBeNull();
		expect(translate("en-GB", "status.submittedOn", { date: "29 October 2026" })).toBe("Submitted on 29 October 2026");
	});

	// Pages already moved to the catalog keep no English text of their own in JSX
	it.each(["src/pages/index.tsx", "src/pages/apply/status.tsx", "src/components/ApplyShell.tsx"])("%s has no hard-coded text", (file) => {
		const source = readFileSync(join(__dirname, "../..", file), "utf8");
		const text = [...source.matchAll(/>\s*([A-Za-z][A-Za-z ,.'’()-]{3,})\s*</g)].map((m) => m[1].trim());
		// the brand name is never translated (translate="no")
		expect(text.filter((word) => word !== "ELOKA")).toEqual([]);
	});
});
