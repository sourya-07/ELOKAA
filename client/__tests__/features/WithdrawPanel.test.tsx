import { fireEvent, screen, waitFor } from "@testing-library/react";
import { WithdrawPanel } from "@/features/apply/WithdrawPanel";
import { useApplication } from "@/features/apply/ApplicationContext";
import { snapshotAt } from "../fixtures";
import { render } from "../render";

jest.mock("@/features/apply/ApplicationContext", () => ({ useApplication: jest.fn() }));

function respond(...replies: { status: number; body: unknown }[]) {
	const fetchMock = jest.fn();
	for (const reply of replies) {
		fetchMock.mockResolvedValueOnce({
			ok: reply.status < 300,
			status: reply.status,
			headers: { get: () => null },
			text: async () => JSON.stringify(reply.body),
		});
	}
	global.fetch = fetchMock as unknown as typeof fetch;
	return fetchMock;
}

describe("withdraw panel", () => {
	it("asks for a fresh code before withdrawing, with the chosen reason", async () => {
		const write = jest.fn().mockResolvedValue(snapshotAt(null, { status: "WITHDRAWN" }));
		(useApplication as jest.Mock).mockReturnValue({ snapshot: snapshotAt(null, { status: "SUBMITTED" }), write });
		const fetchMock = respond({ status: 202, body: { expires_in: 300, otp_length: 6 } });
		render(<WithdrawPanel />);

		fireEvent.click(screen.getByRole("button", { name: "Withdraw application" }));
		fireEvent.change(screen.getByLabelText("Why are you withdrawing?"), { target: { value: "WILL_APPLY_LATER" } });
		fireEvent.click(screen.getByRole("button", { name: "Send code to my mobile" }));
		expect(await screen.findByLabelText("Digit 1 of 6")).toBeInTheDocument();
		expect(fetchMock.mock.calls[0][0]).toBe("/api/v1/partner/reauth");
		expect(screen.getByRole("button", { name: "Withdraw" })).toBeDisabled();

		fireEvent.paste(screen.getByLabelText("Digit 1 of 6"), { clipboardData: { getData: () => "123456" } });
		fireEvent.click(screen.getByRole("button", { name: "Withdraw" }));
		await waitFor(() =>
			expect(write).toHaveBeenCalledWith("/withdraw", {
				body: { otp: "123456", reason_code: "WILL_APPLY_LATER" },
				idempotent: true,
			}),
		);
	});

	it("uses the code length the server sends", async () => {
		(useApplication as jest.Mock).mockReturnValue({ snapshot: snapshotAt(null), write: jest.fn() });
		respond({ status: 202, body: { expires_in: 300, otp_length: 4 } });
		render(<WithdrawPanel />);
		fireEvent.click(screen.getByRole("button", { name: "Withdraw application" }));
		fireEvent.click(screen.getByRole("button", { name: "Send code to my mobile" }));
		expect(await screen.findByLabelText("Digit 1 of 4")).toBeInTheDocument();
		expect(screen.queryByLabelText("Digit 5 of 6")).not.toBeInTheDocument();
	});

	it("can be closed without doing anything", () => {
		const fetchMock = respond();
		(useApplication as jest.Mock).mockReturnValue({ snapshot: snapshotAt(null), write: jest.fn() });
		render(<WithdrawPanel />);
		fireEvent.click(screen.getByRole("button", { name: "Withdraw application" }));
		fireEvent.click(screen.getByRole("button", { name: "Keep my application" }));
		expect(screen.getByRole("button", { name: "Withdraw application" })).toBeInTheDocument();
		expect(fetchMock).not.toHaveBeenCalled();
	});

	it("offers a new code only after the server's wait", async () => {
		(useApplication as jest.Mock).mockReturnValue({ snapshot: snapshotAt(null), write: jest.fn() });
		respond({ status: 202, body: { expires_in: 300, resend_after: 60, otp_length: 6 } });
		render(<WithdrawPanel />);
		fireEvent.click(screen.getByRole("button", { name: "Withdraw application" }));
		fireEvent.click(screen.getByRole("button", { name: "Send code to my mobile" }));
		expect(await screen.findByRole("button", { name: "Resend in 1 min" })).toBeDisabled();
	});
});
