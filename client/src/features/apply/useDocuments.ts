import { useCallback, useEffect, useState } from "react";
import { call } from "@/lib/api";
import type { DocumentView } from "@/lib/types";
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
		async (kind: string, file: File) => {
			const form = new FormData();
			form.append("kind", kind);
			form.append("file", file, file.name);
			await call<DocumentView>(base, { form });
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

	return { documents, upload, remove, reload, contentUrl: (id: string) => `/api${base}/${id}/content` };
}
