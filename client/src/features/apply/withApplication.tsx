import type { ComponentType } from "react";

/** A journey page that needs the applicant's application (and a signed-in session). */
export type ApplicationPage<P> = ComponentType<P> & { needsApplication?: "required" | "optional" };

/**
 * Marks a journey page. `_app` wraps marked pages in one shared ApplicationProvider, so moving
 * between journey pages keeps the loaded application instead of loading it again on every page.
 */
export function withApplication<P extends object>(Page: ComponentType<P>): ApplicationPage<P> {
	const marked = Page as ApplicationPage<P>;
	marked.needsApplication = "required";
	return marked;
}

/** A page that works signed out too (sign-in), and uses the application once there is one. */
export function withOptionalApplication<P extends object>(Page: ComponentType<P>): ApplicationPage<P> {
	const marked = Page as ApplicationPage<P>;
	marked.needsApplication = "optional";
	return marked;
}
