import { createContext, type ReactNode, useCallback, useContext, useMemo, useState } from "react";
import type { MessageKey } from "./en-GB";
import { translate, usableLocale } from "./catalogs";
import { DEFAULT_LOCALE, directionOf, LOCALE_COOKIE, type LocaleCode } from "./locales";

interface I18n {
	locale: LocaleCode;
	setLocale: (code: LocaleCode) => void;
	t: (key: MessageKey, values?: Record<string, string | number>) => string;
}

const Context = createContext<I18n>({
	locale: DEFAULT_LOCALE,
	setLocale: () => undefined,
	t: (key, values) => translate(DEFAULT_LOCALE, key, values),
});

/** The applicant's language for every page: from the cookie on the first render, then their choice. */
export function I18nProvider({ initial, children }: { initial?: LocaleCode | null; children: ReactNode }) {
	const [locale, setState] = useState<LocaleCode>(usableLocale(initial));
	const setLocale = useCallback((code: LocaleCode) => {
		const next = usableLocale(code);
		setState(next);
		try {
			// A year; SameSite=Strict like the session cookies; not personal data
			document.cookie = `${LOCALE_COOKIE}=${next}; path=/; max-age=31536000; SameSite=Strict`;
			document.documentElement.lang = next;
			document.documentElement.dir = directionOf(next);
		} catch {
			// Cookies blocked: the choice still holds for this visit
		}
	}, []);
	const value = useMemo<I18n>(() => ({ locale, setLocale, t: (key, values) => translate(locale, key, values) }), [locale, setLocale]);
	return <Context.Provider value={value}>{children}</Context.Provider>;
}

export function useI18n(): I18n {
	return useContext(Context);
}

/** The cookie's language from a Cookie header (server side) or document.cookie. */
export function localeFromCookie(cookie: string | undefined | null): LocaleCode | null {
	const match = cookie?.match(new RegExp(`(?:^|;\\s*)${LOCALE_COOKIE}=([A-Za-z-]+)`));
	const value = match?.[1];
	return value ? usableLocale(value as LocaleCode) : null;
}
