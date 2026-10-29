/**
 * The languages the selector offers, in this order (owner's list). Each is named in its own script
 * with the English name after it. A language can be chosen only when its catalog is complete and
 * reviewed (i18n/catalogs.ts); until then it is listed as not available yet, never shown in English
 * while claiming to be translated.
 */
export const LOCALES = [
	{ code: "pa-IN", label: "ਪੰਜਾਬੀ (Punjabi)", dir: "ltr" },
	{ code: "en-GB", label: "English (UK)", dir: "ltr" },
	{ code: "hi-IN", label: "हिन्दी (Hindi)", dir: "ltr" },
	{ code: "ur-IN", label: "اردو (Urdu)", dir: "rtl" },
	{ code: "gu-IN", label: "ગુજરાતી (Gujarati)", dir: "ltr" },
	{ code: "bn-IN", label: "বাংলা (Bengali)", dir: "ltr" },
	{ code: "ta-IN", label: "தமிழ் (Tamil)", dir: "ltr" },
] as const;

export type LocaleCode = (typeof LOCALES)[number]["code"];

export const DEFAULT_LOCALE: LocaleCode = "en-GB";

/** Where the choice is kept: a cookie (not personal data), so the server renders the page in it. */
export const LOCALE_COOKIE = "eloka_lang";

export function isLocale(value: unknown): value is LocaleCode {
	return LOCALES.some((locale) => locale.code === value);
}

export function directionOf(code: LocaleCode): "ltr" | "rtl" {
	return LOCALES.find((locale) => locale.code === code)?.dir ?? "ltr";
}
