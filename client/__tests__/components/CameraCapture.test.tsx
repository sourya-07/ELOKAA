import { fireEvent, screen, waitFor } from "@testing-library/react";
import { CameraCapture } from "@/components/CameraCapture";
import { fakeJpeg, jpegFile } from "../files";
import { render } from "../render";

describe("CameraCapture", () => {
	it("with the phone camera, takes the eyes-closed picture first, then the photo, and starts over after", async () => {
		Object.defineProperty(navigator, "mediaDevices", { value: undefined, configurable: true }); // no camera API
		const onCapture = jest.fn().mockResolvedValue(undefined);
		const { container } = render(<CameraCapture onCapture={onCapture} />);
		fireEvent.click(screen.getByRole("button", { name: "Open camera" }));
		const closed = jpegFile(40 * 1024, { name: "closed.jpg" });
		const open = jpegFile(50 * 1024, { name: "open.jpg" });
		expect(screen.getByText("Take a photo with your eyes closed (phone camera)")).toBeInTheDocument();
		fireEvent.change(container.querySelector("input[type=file]") as HTMLInputElement, { target: { files: [closed] } });
		expect(await screen.findByText("Now a photo with your eyes open")).toBeInTheDocument();
		expect(onCapture).not.toHaveBeenCalled();
		fireEvent.change(container.querySelector("input[type=file]") as HTMLInputElement, { target: { files: [open] } });
		await waitFor(() => expect(onCapture).toHaveBeenCalledWith(open, closed));
		expect(await screen.findByRole("button", { name: "Open camera" })).toBeInTheDocument();
		expect(container.querySelector("input[type=file]")).toBeNull();
	});

	it("says what to do when camera access is blocked", async () => {
		const denied = Object.assign(new Error("denied"), { name: "NotAllowedError" });
		Object.defineProperty(navigator, "mediaDevices", { value: { getUserMedia: jest.fn().mockRejectedValue(denied) }, configurable: true });
		render(<CameraCapture onCapture={jest.fn()} />);
		fireEvent.click(screen.getByRole("button", { name: "Open camera" }));
		expect(await screen.findByText(/Camera access is blocked for this site/)).toBeInTheDocument();
		expect(screen.getByRole("button", { name: "Try the camera again" })).toBeInTheDocument();
	});

	it("stops and offers the phone camera when the picture never plays", async () => {
		const track = { stop: jest.fn() };
		const stream = { getTracks: () => [track] };
		Object.defineProperty(navigator, "mediaDevices", { value: { getUserMedia: jest.fn().mockResolvedValue(stream) }, configurable: true });
		const play = jest.spyOn(HTMLMediaElement.prototype, "play").mockRejectedValue(new Error("no frames"));
		render(<CameraCapture onCapture={jest.fn()} />);
		fireEvent.click(screen.getByRole("button", { name: "Open camera" }));
		expect(await screen.findByRole("button", { name: "Try the camera again" })).toBeInTheDocument();
		expect(screen.getByText("Take a photo with your eyes closed (phone camera)")).toBeInTheDocument();
		expect(track.stop).toHaveBeenCalled();
		play.mockRestore();
	});

	it("checks a phone-camera file before sending it", async () => {
		Object.defineProperty(navigator, "mediaDevices", { value: undefined, configurable: true });
		const onCapture = jest.fn();
		const { container } = render(<CameraCapture onCapture={onCapture} />);
		fireEvent.click(screen.getByRole("button", { name: "Open camera" }));
		fireEvent.change(container.querySelector("input[type=file]") as HTMLInputElement, {
			target: { files: [fakeJpeg(50 * 1024)] },
		});
		expect(await screen.findByText("Only JPG or PNG files are accepted.")).toBeInTheDocument();
		expect(onCapture).not.toHaveBeenCalled();
	});
});
