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
});
