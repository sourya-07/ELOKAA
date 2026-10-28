import { AspectRatio, Box, Button, HStack, Stack, Text } from "@chakra-ui/react";
import { useCallback, useEffect, useRef, useState } from "react";
import { messageOf } from "@/lib/api";
import { ErrorBanner } from "./ErrorBanner";

interface Props {
	onCapture: (file: File) => Promise<void>;
	isDisabled?: boolean;
}

/**
 * Takes a live photo with the device camera (never from the gallery when a camera exists).
 * Without camera access it falls back to the phone's camera picker. The picture leaves the
 * browser only when the person presses "Use photo".
 */
export function CameraCapture({ onCapture, isDisabled }: Props) {
	const video = useRef<HTMLVideoElement>(null);
	const stream = useRef<MediaStream | null>(null);
	const [state, setState] = useState<"idle" | "live" | "preview" | "unavailable">("idle");
	const [preview, setPreview] = useState<{ url: string; file: File } | null>(null);
	const [busy, setBusy] = useState(false);
	const [error, setError] = useState<string | null>(null);

	const send = async (file: File) => {
		setBusy(true);
		setError(null);
		try {
			await onCapture(file);
			// Uploaded: back to the start, so "Use photo" cannot send the same picture twice
			setPreview(null);
			setState("idle");
		} catch (e) {
			setError(messageOf(e));
		} finally {
			setBusy(false);
		}
	};

	const stop = useCallback(() => {
		stream.current?.getTracks().forEach((track) => track.stop());
		stream.current = null;
	}, []);

	useEffect(() => stop, [stop]);
	useEffect(() => () => {
		if (preview) {
			URL.revokeObjectURL(preview.url);
		}
	}, [preview]);

	const start = async () => {
		if (!navigator.mediaDevices?.getUserMedia) {
			setState("unavailable");
			return;
		}
		try {
			stream.current = await navigator.mediaDevices.getUserMedia({ video: { facingMode: "user" }, audio: false });
			setState("live");
			requestAnimationFrame(() => {
				if (video.current && stream.current) {
					video.current.srcObject = stream.current;
					void video.current.play();
				}
			});
		} catch {
			setState("unavailable");
		}
	};

	const snap = () => {
		const element = video.current;
		if (!element || !element.videoWidth) {
			return;
		}
		const canvas = document.createElement("canvas");
		canvas.width = element.videoWidth;
		canvas.height = element.videoHeight;
		canvas.getContext("2d")?.drawImage(element, 0, 0);
		canvas.toBlob(
			(blob) => {
				if (blob) {
					const file = new File([blob], "live-photo.jpg", { type: "image/jpeg" });
					setPreview({ url: URL.createObjectURL(blob), file });
					setState("preview");
					stop();
				}
			},
			"image/jpeg",
			0.92,
		);
	};

	const use = async () => {
		if (preview) {
			await send(preview.file);
		}
	};

	return (
		<Stack spacing={3}>
			{state === "live" && (
				<AspectRatio ratio={4 / 3} maxW="100%" bg="ink.900" borderRadius="control" overflow="hidden">
					<video ref={video} muted playsInline aria-label="Camera preview" />
				</AspectRatio>
			)}
			{state === "preview" && preview && (
				<Box borderRadius="control" overflow="hidden" maxW="100%">
					{/* eslint-disable-next-line @next/next/no-img-element -- a local blob preview */}
					<img src={preview.url} alt="Your photo" style={{ width: "100%" }} />
				</Box>
			)}
			<Text fontSize="sm" color="text.muted">
				Face the camera in good light. Remove glasses and caps.
			</Text>
			<HStack wrap="wrap">
				{state === "idle" && (
					<Button variant="outline" size="md" onClick={start} isDisabled={isDisabled}>
						Open camera
					</Button>
				)}
				{state === "live" && (
					<Button size="md" onClick={snap}>
						Take photo
					</Button>
				)}
				{state === "preview" && (
					<>
						<Button size="md" onClick={use} isLoading={busy} loadingText="Uploading">
							Use photo
						</Button>
						<Button size="md" variant="outline" onClick={start} isDisabled={busy}>
							Retake
						</Button>
					</>
				)}
				{state === "unavailable" && (
					<Button as="label" variant="outline" size="md" cursor="pointer">
						Take a photo
						<input
							type="file"
							accept="image/jpeg"
							capture="user"
							hidden
							onChange={async (event) => {
								const file = event.target.files?.[0];
								if (file) {
									await send(file);
								}
							}}
						/>
					</Button>
				)}
			</HStack>
			<ErrorBanner message={error} />
			{state === "unavailable" && (
				<Text fontSize="sm" color="text.muted">
					We could not open the camera here. Allow camera access, or take the photo with your phone camera.
				</Text>
			)}
		</Stack>
	);
}
