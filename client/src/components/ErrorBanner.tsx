import { Alert, AlertDescription, AlertIcon } from "@chakra-ui/react";

export function ErrorBanner({ message, tone = "error" }: { message: string | null; tone?: "error" | "warning" | "info" }) {
	if (!message) {
		return null;
	}
	return (
		<Alert status={tone} borderRadius="control" role="alert">
			<AlertIcon />
			<AlertDescription>{message}</AlertDescription>
		</Alert>
	);
}
