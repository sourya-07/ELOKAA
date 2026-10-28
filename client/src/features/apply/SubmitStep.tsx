import { Button } from "@chakra-ui/react";
import { useRouter } from "next/router";
import { ErrorBanner } from "@/components/ErrorBanner";
import { ApiError } from "@/lib/api";
import { missingText } from "@/lib/documents";
import { routeFor } from "@/lib/steps";
import { useApplication } from "./ApplicationContext";
import { useAction } from "./useAction";

/** "Save and continue" for an upload step: the server checks every required file is there. */
export function SubmitStep({ path, body, isDisabled }: { path: string; body?: unknown; isDisabled?: boolean }) {
	const { write } = useApplication();
	const router = useRouter();
	const { busy, error, run } = useAction();
	const submit = () =>
		run(async () => {
			try {
				const next = await write(path, body === undefined ? {} : { body });
				await router.push(routeFor(next));
			} catch (e) {
				if (e instanceof ApiError && e.problem.missing_documents?.length) {
					throw new ApiError({
						...e.problem,
						detail: missingText(e.problem.missing_documents),
					});
				}
				throw e;
			}
		});
	return (
		<>
			<ErrorBanner message={error} />
			<Button onClick={submit} isLoading={busy} isDisabled={isDisabled}>
				Save and continue
			</Button>
		</>
	);
}
