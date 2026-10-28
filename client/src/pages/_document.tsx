import Document, { type DocumentContext, type DocumentInitialProps, Head, Html, Main, NextScript } from "next/document";

interface Props extends DocumentInitialProps {
	nonce?: string;
}

/** Passes the per-request CSP nonce (set by middleware) to Next.js's own scripts. */
export default class AppDocument extends Document<Props> {
	static async getInitialProps(context: DocumentContext): Promise<Props> {
		const initial = await Document.getInitialProps(context);
		const nonce = context.req?.headers["x-nonce"];
		return { ...initial, nonce: typeof nonce === "string" ? nonce : undefined };
	}

	render() {
		const { nonce } = this.props;
		return (
			<Html lang="en-IN">
				<Head nonce={nonce}>
					<meta name="color-scheme" content="light" />
					<meta name="theme-color" content="#FAFAF7" />
				</Head>
				<body>
					<Main />
					<NextScript nonce={nonce} />
				</body>
			</Html>
		);
	}
}
