import { DOCUMENT_LABEL } from "@/lib/documents";
import { ERROR_TEXT, PAGES } from "@/lib/steps";
import type { SubmitBlocker } from "@/lib/types";

const GROUP_FILES: Record<string, string> = {
	VOTER_ID_OR_DRIVING_LICENCE: "both sides of your voter ID or driving licence",
	GRADUATION_CERTIFICATE: "your graduation certificate",
};

/** One sentence per thing still stopping submission, with where to fix it. */
export function describeBlocker(blocker: SubmitBlocker): { text: string; href: string; title: string } {
	const page = PAGES.find((p) => p.step === blocker.step_key) ?? PAGES[PAGES.length - 1];
	const href = `${page.path.split("#")[0]}?return=review${page.path.includes("#") ? `#${page.path.split("#")[1]}` : ""}`;
	if (blocker.code === "DOCUMENTS_MISSING" && blocker.group) {
		const what = GROUP_FILES[blocker.group] ?? `your ${DOCUMENT_LABEL[blocker.group] ?? blocker.group.toLowerCase().replace(/_/g, " ")}`;
		return { text: `Add ${what}.`, href, title: page.title };
	}
	if (blocker.code === "STEP_BLOCKED") {
		return { text: "Too many failed attempts. Our team will contact you.", href, title: page.title };
	}
	if (blocker.code === "STEP_REOPENED") {
		return { text: "Our team asked for a change here.", href, title: page.title };
	}
	const known = ERROR_TEXT[blocker.code];
	return { text: known ?? "Not done yet.", href, title: page.title };
}
