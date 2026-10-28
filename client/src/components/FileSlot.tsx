import { Box, Button, Flex, HStack, Link, Stack, Tag, Text, VisuallyHidden } from "@chakra-ui/react";
import { useId, useRef, useState } from "react";
import { messageOf } from "@/lib/api";
import type { DocumentView } from "@/lib/types";
import { checkFile, type FileRule } from "@/lib/validation";
import { enter } from "@/styles/motion";
import { ErrorBanner } from "./ErrorBanner";
import { StatusPill } from "./StatusPill";

interface Props {
	label: string;
	rule: FileRule;
	required?: boolean;
	files: DocumentView[];
	onUpload: (file: File) => Promise<void>;
	onRemove?: (documentId: string) => Promise<void>;
	contentUrl: (documentId: string) => string;
	allowMany?: boolean;
	isDisabled?: boolean;
}

/** One kind of file: what is uploaded (with its review status) and a button to add or replace it. */
export function FileSlot({ label, rule, required, files, onUpload, onRemove, contentUrl, allowMany, isDisabled }: Props) {
	const inputId = useId();
	const input = useRef<HTMLInputElement>(null);
	const [busy, setBusy] = useState(false);
	const [error, setError] = useState<string | null>(null);

	const choose = async (file: File | undefined) => {
		if (!file) {
			return;
		}
		const problem = checkFile(file, rule);
		if (problem) {
			setError(problem);
			return;
		}
		setError(null);
		setBusy(true);
		try {
			await onUpload(file);
		} catch (e) {
			setError(messageOf(e));
		} finally {
			setBusy(false);
			if (input.current) {
				input.current.value = "";
			}
		}
	};

	return (
		<Box border="1px dashed" borderColor="ink.300" borderRadius="control" p={4}>
			<Stack spacing={3}>
				<Flex justify="space-between" align="center" gap={2} wrap="wrap">
					<Text fontWeight={600}>{label}</Text>
					<Tag size="sm" borderRadius="full" bg={required ? "connect.50" : "bg.subtle"} color={required ? "connect.500" : "text.muted"}>
						{required ? "Required" : "Optional"}
					</Tag>
				</Flex>
				{files.map((file) => (
					<Flex key={file.document_id} justify="space-between" align="center" gap={2} wrap="wrap" sx={enter(0, 200)}>
						<Stack spacing={0} minW={0}>
							<Link href={contentUrl(file.document_id)} isExternal color="text.link" noOfLines={1}>
								{file.file_name}
							</Link>
							{file.reject_reason && (
								<Text fontSize="sm" color="status.error.fg">
									Rejected: {file.reject_reason.toLowerCase().replace(/_/g, " ")}
								</Text>
							)}
						</Stack>
						<HStack>
							<StatusPill status={file.status} />
							{onRemove && (allowMany || !required) && (
								<Button size="sm" variant="link" onClick={() => onRemove(file.document_id)} isDisabled={isDisabled}>
									Remove
								</Button>
							)}
						</HStack>
					</Flex>
				))}
				<Text fontSize="sm" color="text.muted">
					{rule.label}
				</Text>
				<VisuallyHidden>
					<input
						id={inputId}
						ref={input}
						type="file"
						accept={rule.types.join(",")}
						onChange={(event) => choose(event.target.files?.[0])}
						disabled={isDisabled || busy}
					/>
				</VisuallyHidden>
				<Button
					as="label"
					htmlFor={inputId}
					variant="outline"
					size="md"
					isLoading={busy}
					loadingText="Uploading"
					aria-disabled={isDisabled}
					cursor={isDisabled ? "not-allowed" : "pointer"}
				>
					{files.length && !allowMany ? "Replace file" : allowMany && files.length ? "Add another" : "Choose file"}
				</Button>
				<ErrorBanner message={error} />
			</Stack>
		</Box>
	);
}
