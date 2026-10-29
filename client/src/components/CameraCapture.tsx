import { AspectRatio, Box, Button, HStack, Stack, Text } from "@chakra-ui/react";
import { type ReactNode, useCallback, useEffect, useRef, useState } from "react";
import { messageOf } from "@/lib/api";
import { acceptOf, checkFile, PHOTO } from "@/lib/validation";
import { ErrorBanner } from "./ErrorBanner";
import { frameOf, useBlink } from "./useBlink";

interface Props {
	/** The photo, and the eyes-closed frame of the blink the server checks against it */
	onCapture: (file: File, blinkFrame: Blob) => Promise<void>;
	/** Called when the applicant opens the camera (a button press), e.g. to start the location */
	onStart?: () => void;
	/** False while the photo may not be sent yet (no location); "Use photo" waits */
	canUse?: boolean;
	/** Shown under the picture while it cannot be sent */
	waitingFor?: ReactNode;
	isDisabled?: boolean;
}

/** The longest side of the saved photo: plenty for a face, and well under the 5 MB limit. */
const MAX_SIDE = 1600;

type Problem = "denied" | "missing" | "busy" | "insecure" | "other";

const PROBLEM_TEXT: Record<Problem, string> = {
	denied: "Camera access is blocked for this site. Allow the camera in your browser's site settings (the icon next to the address), then try again.",
	missing: "We could not find a camera on this device. Use a phone or a computer with a camera.",
	busy: "Another app is using the camera. Close it, then try again.",
	insecure: "The camera works only on the secure (https) address of this site.",
	other: "We could not open the camera here.",
};

function problemOf(error: unknown): Problem {
	const name = error instanceof DOMException || error instanceof Error ? error.name : "";
	if (name === "NotAllowedError" || name === "SecurityError") return "denied";
	if (name === "NotFoundError" || name === "OverconstrainedError") return "missing";
	if (name === "NotReadableError" || name === "AbortError") return "busy";
	return "other";
}

/**
 * Takes the live photo with the device camera: a mirrored live view in a 4:5 frame with a face
 * guide, a blink first (seen by useBlink in the browser; its eyes-closed frame goes with the photo for the
 * server to check), a preview to retake or use, and a phone-camera fallback (two pictures: eyes closed,
 * then open) when the browser cannot open the camera. Nothing leaves the browser before "Use photo".
 */
export function CameraCapture({ onCapture, onStart, canUse = true, waitingFor, isDisabled }: Props) {
	const video = useRef<HTMLVideoElement>(null);
	const stream = useRef<MediaStream | null>(null);
	const [state, setState] = useState<"idle" | "live" | "preview" | "unavailable">("idle");
	const [problem, setProblem] = useState<Problem | null>(null);
	const [preview, setPreview] = useState<{ url: string; file: File } | null>(null);
	const [busy, setBusy] = useState(false);
	// The live picture has started (the first frames arrived): only then can a photo be taken
	const [streaming, setStreaming] = useState(false);
	const [error, setError] = useState<string | null>(null);
	const blink = useBlink(video, state === "live" && streaming);
	// No blink detector in this browser: a timed picture with the eyes closed instead
	const [countdown, setCountdown] = useState<number | null>(null);
	// Phone-camera fallback: the eyes-closed picture, kept until the eyes-open one is chosen
	const [fallbackClosed, setFallbackClosed] = useState<File | null>(null);

	const send = async (file: File, blinkFrame: Blob) => {
		setBusy(true);
		setError(null);
		try {
			await onCapture(file, blinkFrame);
			// Uploaded: back to the start, so "Use photo" cannot send the same picture twice
			setPreview(null);
			setState("idle");
			blink.reset();
			setFallbackClosed(null);
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

	// Attach the stream once React has put the <video> on the page (an animation frame could come
	// before that, and the camera would never start); ready once frames actually play. If the
	// picture never plays, stop and offer the retry and the phone-camera fallback
	useEffect(() => {
		const element = video.current;
		if (state === "live" && element && stream.current && element.srcObject !== stream.current) {
			element.srcObject = stream.current;
			element.play().then(
				() => setStreaming(true),
				() => {
					if (element.paused) {
						stop();
						setProblem("other");
						setState("unavailable");
					}
				},
			);
		}
	}, [state, stop]);
	useEffect(() => () => {
		if (preview) {
			URL.revokeObjectURL(preview.url);
		}
	}, [preview]);

	const start = async () => {
		onStart?.();
		setError(null);
		if (typeof window !== "undefined" && window.isSecureContext === false) {
			setProblem("insecure");
			setState("unavailable");
			return;
		}
		if (!navigator.mediaDevices?.getUserMedia) {
			setProblem("other");
			setState("unavailable");
			return;
		}
		try {
			stream.current = await navigator.mediaDevices.getUserMedia({ video: { facingMode: "user" }, audio: false });
			setStreaming(false);
			setProblem(null);
			setState("live");
		} catch (e) {
			setProblem(problemOf(e));
			setState("unavailable");
		}
	};

	const snap = () => {
		const element = video.current;
		if (!element || !element.videoWidth) {
			return;
		}
		const scale = Math.min(1, MAX_SIDE / Math.max(element.videoWidth, element.videoHeight));
		const canvas = document.createElement("canvas");
		canvas.width = Math.round(element.videoWidth * scale);
		canvas.height = Math.round(element.videoHeight * scale);
		// The saved photo is the true image (not mirrored); only the live view is a mirror
		canvas.getContext("2d")?.drawImage(element, 0, 0, canvas.width, canvas.height);
		// Within the photo limit (2 MB): a little more compression only when a detailed picture needs it
		const encode = (quality: number) =>
			canvas.toBlob(
				(blob) => {
					if (blob && blob.size > PHOTO.maxBytes && quality > 0.6) {
						encode(Math.round((quality - 0.1) * 10) / 10);
						return;
					}
					if (blob) {
						const file = new File([blob], "live-photo.jpg", { type: "image/jpeg" });
						setPreview({ url: URL.createObjectURL(blob), file });
						setState("preview");
						stop();
					}
				},
				"image/jpeg",
				quality,
			);
		encode(0.9);
	};

	const use = async () => {
		if (preview && blink.closedFrame) {
			await send(preview.file, blink.closedFrame);
		}
	};

	const retake = () => {
		blink.reset();
		start();
	};

	// Manual blink: press, close the eyes, the frame is taken after 3 seconds
	const closedByTimer = () => {
		setCountdown(3);
		const step = (left: number) => {
			if (left === 0) {
				const element = video.current;
				setCountdown(null);
				if (element && element.videoWidth) {
					frameOf(element).then((frame) => frame && blink.setClosedFrame(frame));
				}
				return;
			}
			setCountdown(left);
			window.setTimeout(() => step(left - 1), 1000);
		};
		step(3);
	};

	const blinkText = (() => {
		if (state !== "live" || !streaming) {
			return null;
		}
		if (blink.closedFrame) {
			return "Blink seen. Keep looking at the camera and take the photo.";
		}
		switch (blink.state.kind) {
			case "loading":
				return "Getting the blink check ready…";
			case "watching":
				return blink.state.faces === 0
					? "Put your face inside the oval."
					: blink.state.faces > 1
						? "Only your face should be in the picture."
						: "Now blink once, slowly.";
			case "unavailable":
				return countdown !== null
					? `Close your eyes now… ${countdown}`
					: "Press the button below, close your eyes for 3 seconds, then open them.";
			default:
				return null;
		}
	})();

	return (
		<Stack spacing={3}>
			{state === "live" && (
				<AspectRatio ratio={4 / 5} maxW="360px" w="100%" bg="ink.900" borderRadius="card" overflow="hidden">
					<Box position="relative">
						<video ref={video} muted playsInline aria-label="Camera preview" onLoadedData={() => setStreaming(true)} style={{ width: "100%", height: "100%", objectFit: "cover", transform: "scaleX(-1)" }} />
						<FaceGuide />
					</Box>
				</AspectRatio>
			)}
			{state === "preview" && preview && (
				<AspectRatio className="camera-preview" ratio={4 / 5} maxW="360px" w="100%" borderRadius="card" overflow="hidden">
					{/* eslint-disable-next-line @next/next/no-img-element -- a local blob preview */}
					<img src={preview.url} alt="Your photo" style={{ objectFit: "cover" }} />
				</AspectRatio>
			)}
			<Text fontSize="sm" color="text.muted">
				Face the camera in good light, with your whole face inside the oval. Remove glasses and caps. You will be asked to blink
				once before the photo.
			</Text>
			{blinkText && (
				<Text role="status" aria-live="polite" fontWeight={600}>
					{blinkText}
				</Text>
			)}
			<HStack wrap="wrap" spacing={3}>
				{state === "idle" && (
					<Button variant="outline" size="md" onClick={start} isDisabled={isDisabled}>
						Open camera
					</Button>
				)}
				{state === "live" && blink.state.kind === "unavailable" && !blink.closedFrame && (
					<Button size="md" variant="outline" onClick={closedByTimer} isDisabled={!streaming || countdown !== null}>
						Take the eyes-closed picture
					</Button>
				)}
				{state === "live" && (
					<Button size="md" onClick={snap} isDisabled={!streaming || !blink.closedFrame} isLoading={!streaming} loadingText="Starting camera">
						Take photo
					</Button>
				)}
				{state === "preview" && (
					<>
						<Button size="md" onClick={use} isLoading={busy} loadingText="Uploading" isDisabled={!canUse}>
							Use photo
						</Button>
						<Button size="md" variant="outline" onClick={retake} isDisabled={busy}>
							Retake
						</Button>
					</>
				)}
				{state === "unavailable" && (
					<>
						<Button variant="outline" size="md" onClick={start}>
							Try the camera again
						</Button>
						<Button as="label" variant="outline" size="md" cursor="pointer" aria-disabled={!canUse}>
							{fallbackClosed ? "Now a photo with your eyes open" : "Take a photo with your eyes closed (phone camera)"}
							<input
								type="file"
								accept={acceptOf(PHOTO)}
								capture="user"
								hidden
								disabled={!canUse}
								onChange={async (event) => {
									const file = event.target.files?.[0];
									event.target.value = "";
									if (!file) {
										return;
									}
									const bad = await checkFile(file, PHOTO);
									if (bad) {
										setError(bad);
										return;
									}
									setError(null);
									// First the eyes-closed picture (the blink), then the photo itself
									if (!fallbackClosed) {
										setFallbackClosed(file);
										return;
									}
									await send(file, fallbackClosed);
								}}
							/>
						</Button>
					</>
				)}
			</HStack>
			{state !== "idle" && !canUse && waitingFor}
			{state === "unavailable" && problem && <ErrorBanner tone="warning" message={PROBLEM_TEXT[problem]} />}
			<ErrorBanner message={error} />
		</Stack>
	);
}

/** An oval to place the face in; decoration only. */
function FaceGuide() {
	return (
		<Box as="svg" viewBox="0 0 100 125" position="absolute" inset={0} w="100%" h="100%" aria-hidden pointerEvents="none">
			<ellipse cx="50" cy="56" rx="30" ry="38" fill="none" stroke="white" strokeOpacity="0.85" strokeWidth="0.8" strokeDasharray="3 2" />
		</Box>
	);
}
