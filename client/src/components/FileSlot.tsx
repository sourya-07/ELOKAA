import { Box, Button, Flex, HStack, Image, Link, Progress, Stack, Tag, Text } from "@chakra-ui/react";
import { useRef, useState } from "react";
import { ApiError, messageOf } from "@/lib/api";
import type { DocumentView } from "@/lib/types";
import { acceptOf, checkFile, type FileRule } from "@/lib/validation";
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
	onUpload: (file: File, onProgress: (share: number) => void, signal: AbortSignal) => Promise<void>;
	onRemove?: (documentId: string) => Promise<void>;
	contentUrl: (documentId: string) => string;
	allowMany?: boolean;
	isDisabled?: boolean;
	/** False when the page already says the formats and sizes once */
	showRule?: boolean;
}

/**
 * One kind of file: what is uploaded (a small preview, its review status, any rejection reason)
 * and a button to add or replace it. Uploads show real progress (and can be cancelled while
 * sending), then "Checking file…" while the server checks it; a failed upload can be tried again
 * with the same file; removing asks once before it happens. Where several files are allowed, the
 * button goes once the server's maximum is reached.
 */
export function FileSlot({ label, hint, rule, required, files, onUpload, onRemove, contentUrl, allowMany, isDisabled, showRule = true }: Props) {
	const input = useRef<HTMLInputElement>(null);
	const [progress, setProgress] = useState<number | null>(null);
	const [busy, setBusy] = useState(false);
	const [confirming, setConfirming] = useState<string | null>(null);
	const [error, setError] = useState<string | null>(null);
	// The file of a failed upload, so "Try again" sends it again without choosing it again
	const [failed, setFailed] = useState<File | null>(null);
	const sending = useRef<AbortController | null>(null);

	const choose = async (file: File | undefined) => {
		if (!file) {
			return;
		}
		const problem = await checkFile(file, rule);
		if (problem) {
			setError(problem);
			setFailed(null);
			return;
		}
		setError(null);
		setFailed(null);
		setBusy(true);
		setProgress(0);
		sending.current = new AbortController();
		try {
			await onUpload(file, setProgress, sending.current.signal);
		} catch (e) {
			if (e instanceof ApiError && e.code === "ABORTED") {
				setError(null);
			} else {
				setError(messageOf(e));
				// A refused file (wrong type, duplicate, too many) gains nothing from a retry; a lost connection or a busy server may
				const retryable = !(e instanceof ApiError) || e.status === 0 || e.status >= 500;
				setFailed(retryable ? file : null);
			}
		} finally {
			sending.current = null;
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
	const full = !!allowMany && rule.maxCount !== undefined && files.length >= rule.maxCount;
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
							<StatusPill status={file.status} forApplicant />
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
						<Flex justify="space-between" align="center" gap={3}>
							<Text fontSize="sm" color="text.muted">
								{(progress ?? 0) < 1 ? `Uploading… ${Math.round((progress ?? 0) * 100)}%` : "Checking file…"}
							</Text>
							{(progress ?? 0) < 1 && (
								<Button size="sm" variant="link" onClick={() => sending.current?.abort()}>
									Cancel
								</Button>
							)}
						</Flex>
					</Stack>
				)}
				{showRule && (
					<Text fontSize="sm" color="text.muted">
						{rule.label}
					</Text>
				)}
				<input ref={input} type="file" hidden accept={acceptOf(rule)} onChange={(event) => choose(event.target.files?.[0])} disabled={isDisabled || busy} />
				{full ? (
					<Text fontSize="sm" color="text.muted">
						You have added {files.length} of {rule.maxCount}.
					</Text>
				) : (
					<Button variant="outline" size="md" onClick={() => input.current?.click()} isDisabled={isDisabled || busy} alignSelf="flex-start">
						{files.length && !allowMany ? "Replace file" : allowMany && files.length ? "Add another" : "Choose file"}
					</Button>
				)}
				<ErrorBanner message={error} />
				{failed && !busy && (
					<Button size="sm" variant="link" alignSelf="flex-start" onClick={() => choose(failed)} isDisabled={isDisabled}>
						Try again
					</Button>
				)}
			</Stack>
		</Box>
	);
}
