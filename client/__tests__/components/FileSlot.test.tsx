import { fireEvent, screen, waitFor } from "@testing-library/react";
import { FileSlot } from "@/components/FileSlot";
import { ApiError } from "@/lib/api";
import { PHOTO } from "@/lib/validation";
import { render } from "../render";

describe("FileSlot", () => {
	it("refuses a PNG before sending it", async () => {
		const onUpload = jest.fn();
		const { container } = render(<FileSlot label="Passport-size photo" rule={PHOTO} required files={[]} onUpload={onUpload} contentUrl={() => ""} />);
		const input = container.querySelector("input[type=file]") as HTMLInputElement;
		fireEvent.change(input, { target: { files: [new File(["x"], "me.png", { type: "image/png" })] } });
		expect(await screen.findByRole("alert")).toHaveTextContent("Only JPG photos are accepted.");
		expect(onUpload).not.toHaveBeenCalled();
	});

	it("uploads a JPG and marks required files", async () => {
		const onUpload = jest.fn().mockResolvedValue(undefined);
		const { container } = render(<FileSlot label="Passport-size photo" rule={PHOTO} required files={[]} onUpload={onUpload} contentUrl={() => ""} />);
		expect(screen.getByText("Required")).toBeInTheDocument();
		fireEvent.change(container.querySelector("input[type=file]") as HTMLInputElement, {
			target: { files: [new File(["x"], "me.jpg", { type: "image/jpeg" })] },
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
		expect(await screen.findByRole("alert")).toHaveTextContent("No connection");
		expect(onRemove).toHaveBeenCalledWith("d1");
	});
});
