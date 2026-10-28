import { fireEvent, screen, waitFor } from "@testing-library/react";
import { CameraCapture } from "@/components/CameraCapture";
import { render } from "../render";

describe("CameraCapture", () => {
	it("starts over after a successful upload, so the same picture is not sent twice", async () => {
		Object.defineProperty(navigator, "mediaDevices", { value: undefined, configurable: true }); // no camera API
		const onCapture = jest.fn().mockResolvedValue(undefined);
		const { container } = render(<CameraCapture onCapture={onCapture} />);
		fireEvent.click(screen.getByRole("button", { name: "Open camera" }));
		const input = container.querySelector("input[type=file]") as HTMLInputElement;
		fireEvent.change(input, { target: { files: [new File(["x"], "me.jpg", { type: "image/jpeg" })] } });
		await waitFor(() => expect(onCapture).toHaveBeenCalledTimes(1));
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

	it("checks a phone-camera file before sending it", async () => {
		Object.defineProperty(navigator, "mediaDevices", { value: undefined, configurable: true });
		const onCapture = jest.fn();
		const { container } = render(<CameraCapture onCapture={onCapture} />);
		fireEvent.click(screen.getByRole("button", { name: "Open camera" }));
		fireEvent.change(container.querySelector("input[type=file]") as HTMLInputElement, {
			target: { files: [new File(["x"], "me.png", { type: "image/png" })] },
		});
		expect(await screen.findByText("Only JPG photos are accepted.")).toBeInTheDocument();
		expect(onCapture).not.toHaveBeenCalled();
	});
});
