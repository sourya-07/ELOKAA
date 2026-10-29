import { fireEvent, screen, waitFor } from "@testing-library/react";
import { ApplicationProvider } from "@/features/apply/ApplicationContext";
import ContactPage from "@/pages/apply/index";
import { snapshotAt } from "../../fixtures";
import { render } from "../../render";

const push = jest.fn();
const replace = jest.fn();
let query: Record<string, string> = {};
jest.mock("next/router", () => ({ useRouter: () => ({ push, replace, query, asPath: "/apply/identity", pathname: "/apply" }) }));

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

const SIGNED_OUT = { status: 401, body: { code: "UNAUTHENTICATED" } };

function renderPage() {
	return render(
		<ApplicationProvider optional>
			<ContactPage />
		</ApplicationProvider>,
	);
}

describe("contact details page", () => {
	beforeEach(() => {
		query = {};
		push.mockReset();
		replace.mockReset();
	});

	it("opens the email again for \"Change\" on the review page instead of sending the applicant away", async () => {
		const done = snapshotAt("DECLARATION");
		done.steps = done.steps.map((s) => (s.step_key === "EMAIL_VERIFICATION" ? { ...s, summary: { email_masked: "r***@example.com" } } : s));
		respond({ status: 200, body: done });
		query = { return: "review" };
		renderPage();
		expect(await screen.findByText("r***@example.com")).toBeInTheDocument();
		expect(screen.getByRole("button", { name: "Change email" })).toBeInTheDocument();
		expect(replace).not.toHaveBeenCalled();
	});

	it("rejects an invalid number without calling the server", async () => {
		const fetchMock = respond(SIGNED_OUT);
		renderPage();
		fireEvent.change(await screen.findByLabelText("Mobile number"), { target: { value: "12345" } });
		fireEvent.click(screen.getByRole("button", { name: "Send code" }));
		expect(screen.getByText("Enter a valid 10-digit mobile number")).toBeInTheDocument();
		expect(fetchMock).toHaveBeenCalledTimes(1); // only the "am I signed in" check
		expect(replace).not.toHaveBeenCalled();
	});

	it("keeps only digits in the mobile field, never more than 10", async () => {
		respond(SIGNED_OUT);
		renderPage();
		const field = await screen.findByLabelText("Mobile number");
		fireEvent.change(field, { target: { value: "11111111111111111111" } });
		expect(field).toHaveValue("1111111111");
		fireEvent.change(field, { target: { value: "98a76" } });
		expect(field).toHaveValue("9876");
	});

	it("signs in with the mobile code and continues on the same card with the email", async () => {
		const fetchMock = respond(
			SIGNED_OUT,
			{ status: 202, body: { otp_ref: "otpref_x", expires_in: 300, resend_after: 60, otp_length: 6 } },
			{ status: 422, body: { code: "OTP_INVALID", detail: "The code is not correct.", attempts_left: 4 } },
			{ status: 200, body: { signed_in: true } },
			{ status: 200, body: snapshotAt("EMAIL_VERIFICATION") },
		);
		renderPage();
		fireEvent.change(await screen.findByLabelText("Mobile number"), { target: { value: "+91 98765 43210" } });
		fireEvent.click(screen.getByRole("button", { name: "Send code" }));
		expect(await screen.findByLabelText("Digit 1 of 6")).toBeInTheDocument();
		expect(JSON.parse(fetchMock.mock.calls[1][1].body)).toEqual({ mobile: "9876543210" });
		// the server's wait, in words: no early resend
		expect(screen.getByRole("button", { name: "Resend in 1 min" })).toBeDisabled();

		fireEvent.paste(screen.getByLabelText("Digit 1 of 6"), { clipboardData: { getData: () => "000000" } });
		fireEvent.click(screen.getByRole("button", { name: "Verify" }));
		expect(await screen.findByText(/4 attempts left/)).toBeInTheDocument();

		fireEvent.paste(screen.getByLabelText("Digit 1 of 6"), { clipboardData: { getData: () => "123456" } });
		fireEvent.click(screen.getByRole("button", { name: "Verify" }));
		expect(await screen.findByLabelText("Email address")).toBeInTheDocument();
		expect(screen.getByText("Mobile number verified")).toBeInTheDocument();
		expect(push).not.toHaveBeenCalled();
	});

	it("proves the email, takes the consent and moves on", async () => {
		const verified = snapshotAt("CONSENT", { version: 4 });
		verified.steps = verified.steps.map((s) => (s.step_key === "EMAIL_VERIFICATION" ? { ...s, summary: { email_masked: "r***@example.com" } } : s));
		const fetchMock = respond(
			{ status: 200, body: snapshotAt("EMAIL_VERIFICATION") },
			{ status: 202, body: { challenge_ref: "c1", expires_in: 600, resend_after: 30, otp_length: 6, email_masked: "r***@example.com" } },
			{ status: 200, body: verified },
			{ status: 200, body: snapshotAt("AADHAAR", { version: 5 }) },
		);
		renderPage();
		fireEvent.change(await screen.findByLabelText("Email address"), { target: { value: "not-an-email" } });
		fireEvent.click(screen.getByRole("button", { name: "Email me a code" }));
		expect(screen.getByText(/Enter a valid email address/)).toBeInTheDocument();

		fireEvent.change(screen.getByLabelText("Email address"), { target: { value: " Ravi@Example.com " } });
		fireEvent.click(screen.getByRole("button", { name: "Email me a code" }));
		expect(await screen.findByLabelText("Digit 1 of 6")).toBeInTheDocument();
		expect(JSON.parse(fetchMock.mock.calls[1][1].body)).toEqual({ email: "ravi@example.com" });

		fireEvent.paste(screen.getByLabelText("Digit 1 of 6"), { clipboardData: { getData: () => "123456" } });
		fireEvent.click(screen.getByRole("button", { name: "Verify email" }));
		expect(await screen.findByRole("checkbox")).toBeInTheDocument(); // consent shows once the email is verified
		expect(screen.getByText(/Email verified/)).toBeInTheDocument();
		expect(fetchMock.mock.calls[2][0]).toBe("/api/v1/partner/applications/ptn_TEST/email-verifications/verify");
		expect(fetchMock.mock.calls[2][1].headers["idempotency-key"]).toBeTruthy();

		fireEvent.click(screen.getByRole("button", { name: "Continue" }));
		expect(await screen.findByText("Agree to how we use your data to continue")).toBeInTheDocument();
		fireEvent.click(screen.getByRole("checkbox"));
		fireEvent.click(screen.getByRole("button", { name: "Continue" }));
		await waitFor(() => expect(push).toHaveBeenCalledWith("/apply/identity"));
	});
});
