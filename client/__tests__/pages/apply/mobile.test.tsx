import { fireEvent, screen, waitFor } from "@testing-library/react";
import MobilePage from "@/pages/apply/index";
import { render } from "../../render";

const push = jest.fn();
jest.mock("next/router", () => ({ useRouter: () => ({ push, replace: jest.fn(), query: {}, pathname: "/apply" }) }));

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

describe("mobile page", () => {
	beforeEach(() => push.mockReset());

	it("rejects an invalid number without calling the server", () => {
		const fetchMock = respond();
		render(<MobilePage />);
		fireEvent.change(screen.getByLabelText("Mobile number"), { target: { value: "12345" } });
		fireEvent.click(screen.getByRole("button", { name: "Send code" }));
		expect(screen.getByText("Enter a valid 10-digit mobile number")).toBeInTheDocument();
		expect(fetchMock).not.toHaveBeenCalled();
	});

	it("sends the code, shows attempts left on a wrong code, and continues on the right one", async () => {
		const fetchMock = respond(
			{ status: 202, body: { otp_ref: "otpref_x", expires_in: 300, resend_after: 30, otp_length: 6 } },
			{ status: 422, body: { code: "OTP_INVALID", detail: "The code is not correct.", attempts_left: 4 } },
			{ status: 200, body: { signed_in: true } },
		);
		render(<MobilePage />);
		fireEvent.change(screen.getByLabelText("Mobile number"), { target: { value: "+91 98765 43210" } });
		fireEvent.click(screen.getByRole("button", { name: "Send code" }));
		expect(await screen.findByLabelText("Digit 1 of 6")).toBeInTheDocument();
		expect(JSON.parse(fetchMock.mock.calls[0][1].body)).toEqual({ mobile: "9876543210" });
		expect(screen.getByRole("button", { name: /Resend code in/ })).toBeDisabled();

		fireEvent.paste(screen.getByLabelText("Digit 1 of 6"), { clipboardData: { getData: () => "000000" } });
		fireEvent.click(screen.getByRole("button", { name: "Verify" }));
		expect(await screen.findByText(/4 attempts left/)).toBeInTheDocument();

		fireEvent.paste(screen.getByLabelText("Digit 1 of 6"), { clipboardData: { getData: () => "123456" } });
		fireEvent.click(screen.getByRole("button", { name: "Verify" }));
		await waitFor(() => expect(push).toHaveBeenCalledWith("/apply/continue"));
	});
});
