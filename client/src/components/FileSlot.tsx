import { Box, Button, Flex, HStack, Image, Link, Progress, Stack, Tag, Text } from "@chakra-ui/react";
import { useRef, useState } from "react";
import { messageOf } from "@/lib/api";
import type { DocumentView } from "@/lib/types";
import { checkFile, type FileRule } from "@/lib/validation";
import { enter } from "@/styles/motion";
import { ErrorBanner } from "./ErrorBanner";
import { StatusPill } from "./StatusPill";

interface Props {
	label: string;
	/** One line under the label, e.g. what the file must show */
	hint?: string;
	rule: FileRule;
	required?: boolean;
	files: DocumentView[];
	onUpload: (file: File, onProgress: (share: number) => void) => Promise<void>;
	onRemove?: (documentId: string) => Promise<void>;
	contentUrl: (documentId: string) => string;
	allowMany?: boolean;
	isDisabled?: boolean;
}

/**
 * One kind of file: what is uploaded (a small preview, its review status, any rejection reason)
 * and a button to add or replace it. Uploads show real progress, then "Checking file…" while the
 * server checks it; removing asks once before it happens.
 */
export function FileSlot({ label, hint, rule, required, files, onUpload, onRemove, contentUrl, allowMany, isDisabled }: Props) {
	const input = useRef<HTMLInputElement>(null);
	const [progress, setProgress] = useState<number | null>(null);
	const [busy, setBusy] = useState(false);
	const [confirming, setConfirming] = useState<string | null>(null);
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
		setProgress(0);
		try {
			await onUpload(file, setProgress);
		} catch (e) {
			setError(messageOf(e));
		} finally {
			setBusy(false);
			setProgress(null);
			if (input.current) {
				input.current.value = "";
			}
		}
	};

	const remove = async (documentId: string) => {
		if (!onRemove) {
			return;
		}
		setError(null);
		setBusy(true);
		try {
			await onRemove(documentId);
			setConfirming(null);
		} catch (e) {
			setError(messageOf(e));
		} finally {
			setBusy(false);
		}
	};

	const uploading = progress !== null;
	return (
		<Box border="1px dashed" borderColor="ink.300" borderRadius="control" p={4}>
			<Stack spacing={3}>
				<Flex justify="space-between" align="center" gap={2} wrap="wrap">
					<Text fontWeight={600}>{label}</Text>
					<Tag size="sm" borderRadius="full" bg={required ? "connect.50" : "bg.subtle"} color={required ? "connect.500" : "text.muted"}>
						{required ? "Required" : "Optional"}
					</Tag>
				</Flex>
				{hint && (
					<Text fontSize="sm" color="text.muted">
						{hint}
					</Text>
				)}
				{files.map((file) => (
					<Flex key={file.document_id} justify="space-between" align="center" gap={3} wrap="wrap" sx={enter(0, 200)}>
						<HStack spacing={3} minW={0}>
							{file.content_type === "image/jpeg" ? (
								<Image src={contentUrl(file.document_id)} alt="" boxSize="48px" objectFit="cover" borderRadius="control" bg="bg.subtle" />
							) : (
								<Flex boxSize="48px" align="center" justify="center" borderRadius="control" bg="bg.subtle" fontSize="xs" fontWeight={700} color="text.muted" aria-hidden>
									PDF
								</Flex>
							)}
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
						</HStack>
						<HStack>
							<StatusPill status={file.status} />
							{onRemove && (allowMany || !required) && confirming !== file.document_id && (
								<Button size="sm" variant="link" onClick={() => setConfirming(file.document_id)} isDisabled={isDisabled || busy}>
									Remove
								</Button>
							)}
							{confirming === file.document_id && (
								<HStack spacing={2} role="group" aria-label={`Remove ${file.file_name}?`}>
									<Button size="sm" variant="link" color="status.error.fg" onClick={() => remove(file.document_id)} isLoading={busy}>
										Yes, remove
									</Button>
									<Button size="sm" variant="link" onClick={() => setConfirming(null)} isDisabled={busy}>
										Keep
									</Button>
								</HStack>
							)}
						</HStack>
					</Flex>
				))}
				{uploading && (
					<Stack spacing={1} role="status" aria-live="polite">
						<Progress value={Math.round((progress ?? 0) * 100)} size="xs" borderRadius="full" colorScheme="blue" aria-label={`Uploading ${label}`} />
						<Text fontSize="sm" color="text.muted">
							{(progress ?? 0) < 1 ? `Uploading… ${Math.round((progress ?? 0) * 100)}%` : "Checking file…"}
						</Text>
					</Stack>
				)}
				<Text fontSize="sm" color="text.muted">
					{rule.label}
				</Text>
				<input ref={input} type="file" hidden accept={rule.types.join(",")} onChange={(event) => choose(event.target.files?.[0])} disabled={isDisabled || busy} />
				<Button variant="outline" size="md" onClick={() => input.current?.click()} isDisabled={isDisabled || busy} alignSelf="flex-start">
					{files.length && !allowMany ? "Replace file" : allowMany && files.length ? "Add another" : "Choose file"}
				</Button>
				<ErrorBanner message={error} />
			</Stack>
		</Box>
	);
}
