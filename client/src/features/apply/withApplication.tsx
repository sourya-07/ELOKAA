import type { ComponentType } from "react";

/** A journey page that needs the applicant's application (and a signed-in session). */
export type ApplicationPage<P> = ComponentType<P> & { needsApplication?: true };

/**
 * Marks a journey page. `_app` wraps marked pages in one shared ApplicationProvider, so moving
 * between journey pages keeps the loaded application instead of loading it again on every page.
 */
export function withApplication<P extends object>(Page: ComponentType<P>): ApplicationPage<P> {
	const marked = Page as ApplicationPage<P>;
	marked.needsApplication = true;
	return marked;
}
