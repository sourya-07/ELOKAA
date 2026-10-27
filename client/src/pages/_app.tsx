import { ChakraProvider } from "@chakra-ui/react";
import NextApp, { type AppContext, type AppProps } from "next/app";
import { theme } from "@/styles/theme";

export default function App({ Component, pageProps }: AppProps) {
	return (
		<ChakraProvider theme={theme}>
			<Component {...pageProps} />
		</ChakraProvider>
	);
}

/**
 * Render every page per request: each response carries its own CSP nonce (middleware.ts,
 * _document.tsx), which a page built ahead of time could not have.
 */
App.getInitialProps = async (context: AppContext) => NextApp.getInitialProps(context);
