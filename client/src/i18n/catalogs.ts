import { enGB, type MessageKey, type Messages } from "./en-GB";
import { DEFAULT_LOCALE, LOCALES, type LocaleCode } from "./locales";

/**
 * A translation: its messages and whether a person who reads the language has reviewed them. Add a
 * language here only with real translations (never machine output passed off as reviewed).
 */
export interface Catalog {
	messages: Partial<Messages>;
	reviewed: boolean;
}

export const CATALOGS: Partial<Record<LocaleCode, Catalog>> = {
	"en-GB": { messages: enGB, reviewed: true },
};

const KEYS = Object.keys(enGB) as MessageKey[];

/** Share of the English keys a language has (0 to 1). */
export function coverage(code: LocaleCode): number {
	const messages = CATALOGS[code]?.messages ?? {};
	return KEYS.filter((key) => typeof messages[key] === "string" && messages[key] !== "").length / KEYS.length;
}

/** A language can be chosen only when every message is there and a person reviewed them. */
export function isAvailable(code: LocaleCode): boolean {
	return !!CATALOGS[code]?.reviewed && coverage(code) === 1;
}

/** The language to show: the chosen one if it can be used, otherwise English (UK). */
export function usableLocale(code: LocaleCode | null | undefined): LocaleCode {
	return code && isAvailable(code) ? code : DEFAULT_LOCALE;
}

/** The message in the language, with {placeholders} filled in. */
export function translate(code: LocaleCode, key: MessageKey, values: Record<string, string | number> = {}): string {
	const text = CATALOGS[code]?.messages[key] ?? enGB[key];
	return text.replace(/\{(\w+)\}/g, (match, name: string) => (name in values ? String(values[name]) : match));
}

export const AVAILABLE = LOCALES.filter((locale) => isAvailable(locale.code)).map((locale) => locale.code);
