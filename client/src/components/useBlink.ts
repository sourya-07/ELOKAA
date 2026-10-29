import { type RefObject, useCallback, useEffect, useRef, useState } from "react";

/** Where the self-hosted MediaPipe files are (scripts/copy-mediapipe.mjs; the CSP allows only 'self'). */
const WASM = "/mediapipe/wasm";
const MODEL = "/mediapipe/face_landmarker.task";

/** Eyes count as closed above this blink score, and open again below the lower one. */
const CLOSED = 0.5;
const OPEN = 0.25;
/** The eyes-closed frame is small: the server only looks for the face and the eyes in it. */
const FRAME_SIDE = 480;
const EVERY_MS = 66;

export type BlinkState =
	| { kind: "off" }
	| { kind: "loading" }
	/** The detector could not start (old browser, no WebAssembly): the manual eyes-closed capture is used */
	| { kind: "unavailable" }
	| { kind: "watching"; faces: number }
	| { kind: "blinked" };

interface Detector {
	detectForVideo(video: HTMLVideoElement, timestamp: number): {
		faceLandmarks: unknown[];
		faceBlendshapes?: { categories: { categoryName: string; score: number }[] }[];
	};
	close(): void;
}

/** The current video frame as a small JPEG (not mirrored: the true picture, like the photo). */
export function frameOf(video: HTMLVideoElement, side = FRAME_SIDE): Promise<Blob | null> {
	const scale = Math.min(1, side / Math.max(video.videoWidth, video.videoHeight));
	const canvas = document.createElement("canvas");
	canvas.width = Math.round(video.videoWidth * scale);
	canvas.height = Math.round(video.videoHeight * scale);
	canvas.getContext("2d")?.drawImage(video, 0, 0, canvas.width, canvas.height);
	return new Promise((resolve) => canvas.toBlob(resolve, "image/jpeg", 0.85));
}

/**
 * Watches the live camera picture for one blink (eyes open, closed, open again) with MediaPipe's face
 * landmarks, in this browser only (nothing is sent while watching), and keeps the eyes-closed frame for the
 * server, which checks it is the same face as the photo. A blink seen in a browser can be faked; it is a
 * check that raises the bar, not proof of liveness (docs/OPEN_QUESTIONS.md C-12).
 */
export function useBlink(video: RefObject<HTMLVideoElement | null>, active: boolean) {
	const [state, setState] = useState<BlinkState>({ kind: "off" });
	const [closedFrame, setClosedFrame] = useState<Blob | null>(null);
	const detector = useRef<Detector | null>(null);
	const phase = useRef<"wait-open" | "wait-closed" | "wait-reopen" | "done">("wait-open");
	const frame = useRef<Blob | null>(null);

	/** Forgets the blink (a retake): watching starts again the next time the camera is live. */
	const reset = useCallback(() => {
		phase.current = "wait-open";
		frame.current = null;
		setClosedFrame(null);
	}, []);

	useEffect(() => {
		if (!active) {
			setState({ kind: "off" });
			return;
		}
		let stopped = false;
		let timer: number | undefined;
		setState({ kind: "loading" });
		(async () => {
			try {
				if (!detector.current) {
					const vision = await import("@mediapipe/tasks-vision");
					const files = await vision.FilesetResolver.forVisionTasks(WASM);
					detector.current = (await vision.FaceLandmarker.createFromOptions(files, {
						baseOptions: { modelAssetPath: MODEL, delegate: "CPU" },
						runningMode: "VIDEO",
						numFaces: 2,
						outputFaceBlendshapes: true,
					})) as unknown as Detector;
				}
			} catch {
				if (!stopped) {
					setState({ kind: "unavailable" });
				}
				return;
			}
			if (stopped) {
				return;
			}
			phase.current = "wait-open";
			setState({ kind: "watching", faces: 0 });
			const tick = async () => {
				if (stopped) {
					return;
				}
				const element = video.current;
				if (element && element.readyState >= 2 && detector.current && phase.current !== "done") {
					const result = detector.current.detectForVideo(element, performance.now());
					const faces = result.faceLandmarks.length;
					const shapes = result.faceBlendshapes?.[0]?.categories ?? [];
					const score = (name: string) => shapes.find((c) => c.categoryName === name)?.score ?? 0;
					const blink = faces === 1 ? (score("eyeBlinkLeft") + score("eyeBlinkRight")) / 2 : 0;
					if (faces !== 1) {
						phase.current = "wait-open"; // a blink counts only with one face throughout
					} else if (phase.current === "wait-open" && blink < OPEN) {
						phase.current = "wait-closed";
					} else if (phase.current === "wait-closed" && blink > CLOSED) {
						phase.current = "wait-reopen";
						frame.current = await frameOf(element); // the eyes-closed moment
					} else if (phase.current === "wait-reopen" && blink < OPEN && frame.current) {
						phase.current = "done";
						setClosedFrame(frame.current);
						setState({ kind: "blinked" });
						return;
					}
					setState((current) => (current.kind === "watching" && current.faces === faces ? current : { kind: "watching", faces }));
				}
				timer = window.setTimeout(tick, EVERY_MS);
			};
			tick();
		})();
		return () => {
			stopped = true;
			window.clearTimeout(timer);
		};
	}, [active, video]);

	useEffect(() => () => detector.current?.close(), []);

	return { state, closedFrame, reset, setClosedFrame };
}
