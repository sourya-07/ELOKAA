import { useCallback, useState } from "react";
import { messageOf } from "@/lib/api";

/** Busy flag and a person-readable error around one async action (double clicks are ignored). */
export function useAction() {
	const [busy, setBusy] = useState(false);
	const [error, setError] = useState<string | null>(null);
	const run = useCallback(
		async <T,>(action: () => Promise<T>): Promise<T | undefined> => {
			if (busy) {
				return undefined;
			}
			setBusy(true);
			setError(null);
			try {
				return await action();
			} catch (e) {
				setError(messageOf(e));
				return undefined;
			} finally {
				setBusy(false);
			}
		},
		[busy],
	);
	return { busy, error, setError, run };
}
