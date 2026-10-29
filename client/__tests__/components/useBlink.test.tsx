import { act, renderHook, waitFor } from "@testing-library/react";
import { useBlink } from "@/components/useBlink";

// One face whose blink score goes open, closed, open (what MediaPipe's face blendshapes report)
const scores = [0.05, 0.05, 0.8, 0.9, 0.1, 0.05];
let faces = 1;
jest.mock("@mediapipe/tasks-vision", () => ({
	FilesetResolver: { forVisionTasks: jest.fn().mockResolvedValue({}) },
	FaceLandmarker: {
		createFromOptions: jest.fn().mockResolvedValue({
			detectForVideo: () => {
				const blink = scores.length > 1 ? scores.shift()! : scores[0];
				return {
					faceLandmarks: Array.from({ length: faces }, () => []),
					faceBlendshapes: [{ categories: [{ categoryName: "eyeBlinkLeft", score: blink }, { categoryName: "eyeBlinkRight", score: blink }] }],
				};
			},
			close: jest.fn(),
		}),
	},
}));

beforeAll(() => {
	HTMLCanvasElement.prototype.getContext = jest.fn(() => ({ drawImage: jest.fn() })) as unknown as typeof HTMLCanvasElement.prototype.getContext;
	HTMLCanvasElement.prototype.toBlob = function (callback: BlobCallback) {
		callback(new Blob(["closed"], { type: "image/jpeg" }));
	};
});

function video() {
	return { current: { readyState: 4, videoWidth: 640, videoHeight: 480 } as unknown as HTMLVideoElement };
}

describe("useBlink", () => {
	it("keeps the eyes-closed frame once the eyes have opened, closed and opened again", async () => {
		const ref = video(); // one ref, as a component keeps it
		const { result } = renderHook(() => useBlink(ref, true));
		await waitFor(() => expect(result.current.state.kind).toBe("blinked"), { timeout: 3000 });
		expect(result.current.closedFrame).toBeInstanceOf(Blob);
		act(() => result.current.reset());
		expect(result.current.closedFrame).toBeNull();
	});

	it("does nothing while the camera is not live", () => {
		const ref = video();
		const { result } = renderHook(() => useBlink(ref, false));
		expect(result.current.state.kind).toBe("off");
		expect(result.current.closedFrame).toBeNull();
	});

	it("asks for one face when there are two", async () => {
		faces = 2;
		scores.splice(0, scores.length, 0.05);
		const ref = video();
		const { result } = renderHook(() => useBlink(ref, true));
		await waitFor(() => expect(result.current.state).toEqual({ kind: "watching", faces: 2 }));
		expect(result.current.closedFrame).toBeNull();
		faces = 1;
	});
});
