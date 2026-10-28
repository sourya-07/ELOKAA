import { fireEvent, screen, waitFor } from "@testing-library/react";
import { useApplication } from "@/features/apply/ApplicationContext";
import { PanForm } from "@/features/apply/PanForm";
import { snapshotAt } from "../fixtures";
import { render } from "../render";

jest.mock("@/features/apply/ApplicationContext", () => ({ useApplication: jest.fn() }));

describe("PAN form", () => {
	it("checks the PAN before sending it, with the error under the field", () => {
		const write = jest.fn();
		(useApplication as jest.Mock).mockReturnValue({ snapshot: snapshotAt("PAN"), write });
		render(<PanForm />);
		fireEvent.change(screen.getByLabelText("PAN"), { target: { value: "ABCDE1234F" } }); // not a personal PAN
		fireEvent.click(screen.getByRole("button", { name: "Verify PAN" }));
		expect(screen.getByLabelText("PAN")).toHaveAttribute("aria-invalid", "true");
		expect(screen.getByText("Enter your personal PAN, for example ABCPK1234L")).toBeInTheDocument();
		expect(write).not.toHaveBeenCalled();
	});

	it("sends only the PAN, once per attempt", async () => {
		const write = jest.fn().mockResolvedValue(snapshotAt("CREDIT_CHECK"));
		(useApplication as jest.Mock).mockReturnValue({ snapshot: snapshotAt("PAN"), write });
		render(<PanForm />);
		fireEvent.change(screen.getByLabelText("PAN"), { target: { value: "abcpk1234l" } });
		fireEvent.click(screen.getByRole("button", { name: "Verify PAN" }));
		await waitFor(() => expect(write).toHaveBeenCalledWith("/pan-verifications", { body: { pan: "ABCPK1234L" }, idempotent: true }));
	});
});
