import type { ComponentType } from "react";
import { ApplicationProvider } from "./ApplicationContext";

/** Wraps a journey page so it has the applicant's application (and a signed-in session). */
export function withApplication<P extends object>(Page: ComponentType<P>) {
	function Wrapped(props: P) {
		return (
			<ApplicationProvider>
				<Page {...props} />
			</ApplicationProvider>
		);
	}
	Wrapped.displayName = `withApplication(${Page.displayName ?? Page.name})`;
	return Wrapped;
}
