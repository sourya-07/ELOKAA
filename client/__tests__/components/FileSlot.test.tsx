import { act, fireEvent, screen, waitFor } from "@testing-library/react";
import { FileSlot } from "@/components/FileSlot";
import { ApiError } from "@/lib/api";
import { PAPER, PHOTO } from "@/lib/validation";
import { fakeJpeg, jpegFile, pdfFile } from "../files";
import { render } from "../render";

describe("FileSlot", () => {
	it("refuses a file that is not really a picture before sending it", async () => {
		const onUpload = jest.fn();
		const { container } = render(<FileSlot label="Passport-size photo" rule={PHOTO} required files={[]} onUpload={onUpload} contentUrl={() => ""} />);
		const input = container.querySelector("input[type=file]") as HTMLInputElement;
		fireEvent.change(input, { target: { files: [fakeJpeg(50 * 1024)] } });
		expect(await screen.findByRole("alert")).toHaveTextContent("Only JPG or PNG files are accepted.");
		expect(onUpload).not.toHaveBeenCalled();
	});

	it("refuses a photo under 20 KB before sending it", async () => {
		const onUpload = jest.fn();
		const { container } = render(<FileSlot label="Passport-size photo" rule={PHOTO} required files={[]} onUpload={onUpload} contentUrl={() => ""} />);
		fireEvent.change(container.querySelector("input[type=file]") as HTMLInputElement, { target: { files: [jpegFile(5 * 1024)] } });
		expect(await screen.findByRole("alert")).toHaveTextContent("smaller than 20 KB");
		expect(onUpload).not.toHaveBeenCalled();
	});

	it("uploads a JPG and marks required files", async () => {
		const onUpload = jest.fn().mockResolvedValue(undefined);
		const { container } = render(<FileSlot label="Passport-size photo" rule={PHOTO} required files={[]} onUpload={onUpload} contentUrl={() => ""} />);
		expect(screen.getByText("Required")).toBeInTheDocument();
		fireEvent.change(container.querySelector("input[type=file]") as HTMLInputElement, {
			target: { files: [jpegFile(50 * 1024)] },
		});
		await waitFor(() => expect(onUpload).toHaveBeenCalledTimes(1));
	});

	it("shows the review status and a rejection reason", () => {
		render(
			<FileSlot
				label="Graduation certificate"
				rule={PHOTO}
				files={[{ document_id: "d1", kind: "GRADUATION_CERTIFICATE", status: "REJECTED", file_name: "degree.jpg", content_type: "image/jpeg", size: 10, reject_reason: "BLURRY", uploaded_at: "" }]}
				onUpload={jest.fn()}
				contentUrl={(id) => `/api/x/${id}`}
			/>,
		);
		expect(screen.getByText("Rejected: blurry")).toBeInTheDocument();
		expect(screen.getByText("Optional")).toBeInTheDocument();
	});

	it("shows why a removal failed instead of failing silently", async () => {
		const onRemove = jest.fn().mockRejectedValue(new ApiError({ status: 0, code: "NETWORK", detail: "No connection. Check your internet and try again." }));
		render(
			<FileSlot
				label="Computer certificate"
				rule={PHOTO}
				allowMany
				files={[{ document_id: "d1", kind: "COMPUTER_CERTIFICATE", status: "MANUAL_REVIEW_REQUIRED", file_name: "ccc.jpg", content_type: "image/jpeg", size: 10, uploaded_at: "" }]}
				onUpload={jest.fn()}
				onRemove={onRemove}
				contentUrl={(id) => `/api/x/${id}`}
			/>,
		);
		fireEvent.click(screen.getByRole("button", { name: "Remove" }));
		expect(onRemove).not.toHaveBeenCalled(); // asks first
		fireEvent.click(screen.getByRole("button", { name: "Yes, remove" }));
		expect(await screen.findByRole("alert")).toHaveTextContent("No connection");
		expect(onRemove).toHaveBeenCalledWith("d1");
	});

	it("names the types and sizes beside every upload, and offers them in the picker", () => {
		const { container } = render(<FileSlot label="PAN card" rule={PAPER} required files={[]} onUpload={jest.fn()} contentUrl={() => ""} />);
		expect(screen.getByText("JPG, PNG or PDF, 10 KB to 5 MB")).toBeInTheDocument();
		expect(container.querySelector("input[type=file]")?.getAttribute("accept")).toContain(".png");
	});

	it("shows real upload progress, then that the file is being checked", async () => {
		let report: (share: number) => void = () => undefined;
		let finish: () => void = () => undefined;
		const onUpload = jest.fn((_file: File, onProgress: (share: number) => void) => {
			report = onProgress;
			return new Promise<void>((resolve) => (finish = resolve));
		});
		const { container } = render(<FileSlot label="PAN card" rule={PHOTO} required files={[]} onUpload={onUpload} contentUrl={() => ""} />);
		fireEvent.change(container.querySelector("input[type=file]") as HTMLInputElement, {
			target: { files: [jpegFile(50 * 1024)] },
		});
		await waitFor(() => expect(onUpload).toHaveBeenCalled()); // after the file is checked
		act(() => report(0.4));
		expect(screen.getByText("Uploading… 40%")).toBeInTheDocument();
		act(() => report(1));
		expect(screen.getByText("Checking file…")).toBeInTheDocument();
		await act(async () => finish());
		expect(screen.queryByText("Checking file…")).not.toBeInTheDocument();
	});

	it("stops offering more files at the server's maximum", () => {
		const files = Array.from({ length: 5 }, (_, i) => ({ document_id: `d${i}`, kind: "COMPUTER_CERTIFICATE", status: "MANUAL_REVIEW_REQUIRED", file_name: `c${i}.pdf`, content_type: "application/pdf", size: 10, uploaded_at: "" }));
		render(<FileSlot label="Computer certificates" rule={{ ...PAPER, maxCount: 5 }} allowMany files={files} onUpload={jest.fn()} contentUrl={() => ""} />);
		expect(screen.getByText("You have added 5 of 5.")).toBeInTheDocument();
		expect(screen.queryByRole("button", { name: "Add another" })).not.toBeInTheDocument();
	});

	it("can cancel while sending, without an error", async () => {
		const onUpload = jest.fn(
			(_file: File, _progress: (share: number) => void, signal: AbortSignal) =>
				new Promise<void>((_resolve, reject) => signal.addEventListener("abort", () => reject(new ApiError({ status: 0, code: "ABORTED", detail: "Upload cancelled." })))),
		);
		const { container } = render(<FileSlot label="PAN card" rule={PAPER} required files={[]} onUpload={onUpload} contentUrl={() => ""} />);
		fireEvent.change(container.querySelector("input[type=file]") as HTMLInputElement, { target: { files: [pdfFile(50 * 1024)] } });
		fireEvent.click(await screen.findByRole("button", { name: "Cancel" }));
		await waitFor(() => expect(screen.queryByText(/Uploading/)).not.toBeInTheDocument());
		expect(screen.queryByRole("alert")).not.toBeInTheDocument();
	});

	it("sends the same file again after a lost connection, but not after a refusal", async () => {
		const onUpload = jest
			.fn()
			.mockRejectedValueOnce(new ApiError({ status: 0, code: "NETWORK", detail: "No connection. Check your internet and try again." }))
			.mockRejectedValueOnce(new ApiError({ status: 422, code: "DUPLICATE_FILE", detail: "You already uploaded this same file for another document." }));
		const { container } = render(<FileSlot label="PAN card" rule={PAPER} required files={[]} onUpload={onUpload} contentUrl={() => ""} />);
		const file = pdfFile(50 * 1024);
		fireEvent.change(container.querySelector("input[type=file]") as HTMLInputElement, { target: { files: [file] } });
		fireEvent.click(await screen.findByRole("button", { name: "Try again" }));
		await waitFor(() => expect(onUpload).toHaveBeenCalledTimes(2));
		expect(onUpload.mock.calls[1][0]).toBe(file);
		expect(await screen.findByRole("alert")).toHaveTextContent("same file");
		expect(screen.queryByRole("button", { name: "Try again" })).not.toBeInTheDocument();
	});

	it("states the rule it is given", () => {
		render(<FileSlot label="Passport-size photo" rule={PHOTO} required files={[]} onUpload={jest.fn()} contentUrl={() => ""} />);
		expect(screen.getByText("JPG or PNG, 20 KB to 2 MB, at least 300 × 300 px")).toBeInTheDocument();
	});
});
