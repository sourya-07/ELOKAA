import { ChakraProvider } from "@chakra-ui/react";
import NextApp, { type AppContext, type AppProps } from "next/app";
import { ApplicationProvider } from "@/features/apply/ApplicationContext";
import type { ApplicationPage } from "@/features/apply/withApplication";
import { theme } from "@/styles/theme";

export default function App({ Component, pageProps }: AppProps) {
	const page = <Component {...pageProps} />;
	const needs = (Component as ApplicationPage<object>).needsApplication;
	return (
		<ChakraProvider theme={theme}>
			{/* One provider across journey pages: it stays mounted while only the page inside changes */}
			{needs ? <ApplicationProvider optional={needs === "optional"}>{page}</ApplicationProvider> : page}
		</ChakraProvider>
	);
}

/**
 * Render every page per request: each response carries its own CSP nonce (middleware.ts,
 * _document.tsx), which a page built ahead of time could not have.
 */
App.getInitialProps = async (context: AppContext) => NextApp.getInitialProps(context);
