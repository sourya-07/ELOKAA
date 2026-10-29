import { ChakraProvider } from "@chakra-ui/react";
import { MotionConfig } from "framer-motion";
import NextApp, { type AppContext, type AppInitialProps, type AppProps } from "next/app";
import { ApplicationProvider } from "@/features/apply/ApplicationContext";
import type { ApplicationPage } from "@/features/apply/withApplication";
import { I18nProvider, localeFromCookie } from "@/i18n/I18nProvider";
import type { LocaleCode } from "@/i18n/locales";
import { theme } from "@/styles/theme";

export default function App({ Component, pageProps, locale }: AppProps & { locale?: LocaleCode | null }) {
	const page = <Component {...pageProps} />;
	const needs = (Component as ApplicationPage<object>).needsApplication;
	return (
		<ChakraProvider theme={theme}>
			{/* Chakra's own animations (Collapse, modals, menus) follow the person's reduced-motion setting too */}
			<MotionConfig reducedMotion="user">
				<I18nProvider initial={locale}>
					{/* One provider across journey pages: it stays mounted while only the page inside changes */}
					{needs ? <ApplicationProvider optional={needs === "optional"}>{page}</ApplicationProvider> : page}
				</I18nProvider>
			</MotionConfig>
		</ChakraProvider>
	);
}

/**
 * Render every page per request: each response carries its own CSP nonce (middleware.ts,
 * _document.tsx), which a page built ahead of time could not have.
 */
App.getInitialProps = async (context: AppContext): Promise<AppInitialProps & { locale: LocaleCode | null }> => {
	const initial = await NextApp.getInitialProps(context);
	// The chosen language from its cookie (on the server), so the first render is already in it
	const cookie = context.ctx.req?.headers.cookie ?? (typeof document === "undefined" ? null : document.cookie);
	return { ...initial, locale: localeFromCookie(cookie) };
};
