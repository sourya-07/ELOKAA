import { useCallback, useEffect, useState } from "react";
import { call, uploadWithProgress } from "@/lib/api";
import type { DocumentView } from "@/lib/types";
import { type FileRule, ruleFrom, type ServerRule } from "@/lib/validation";
import { useApplication } from "./ApplicationContext";

/** The application's current files, with upload and remove that keep the snapshot in step. */
export function useDocuments() {
	const { snapshot, refresh } = useApplication();
	const base = `/v1/partner/applications/${snapshot.application_id}/documents`;
	const [documents, setDocuments] = useState<DocumentView[]>([]);

	const reload = useCallback(async () => {
		const reply = await call<DocumentView[]>(base);
		setDocuments(reply.data);
	}, [base]);

	useEffect(() => {
		reload().catch(() => setDocuments([]));
	}, [reload]);

	const upload = useCallback(
		async (
			kind: string,
			file: File,
			fields: Record<string, string> = {},
			onProgress?: (share: number) => void,
			signal?: AbortSignal,
			extraFiles: Record<string, Blob> = {},
		) => {
			const form = new FormData();
			form.append("kind", kind);
			form.append("file", file, file.name);
			Object.entries(fields).forEach(([name, value]) => form.append(name, value));
			Object.entries(extraFiles).forEach(([name, blob]) => form.append(name, blob, `${name}.jpg`));
			await uploadWithProgress<DocumentView>(base, form, onProgress, signal);
			await Promise.all([reload(), refresh()]);
		},
		[base, refresh, reload],
	);

	const remove = useCallback(
		async (documentId: string) => {
			await call(`${base}/${documentId}`, { method: "DELETE" });
			await Promise.all([reload(), refresh()]);
		},
		[base, refresh, reload],
	);

	const [rules, setRules] = useState<Record<string, FileRule>>({});
	useEffect(() => {
		call<ServerRule[]>("/v1/partner/document-rules")
			.then((reply) => setRules(Object.fromEntries(reply.data.map((rule) => [rule.kind, ruleFrom(rule)]))))
			.catch(() => setRules({})); // the built-in copy of the rules stays; the server checks every file anyway
	}, []);
	/** The server's rule for a kind, or the built-in copy until it has loaded. */
	const ruleFor = useCallback((kind: string, fallback: FileRule) => rules[kind] ?? fallback, [rules]);

	return { documents, upload, remove, reload, ruleFor, contentUrl: (id: string) => `/api${base}/${id}/content` };
}
