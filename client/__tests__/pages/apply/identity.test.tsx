import { fireEvent, screen, waitFor } from "@testing-library/react";
import { useApplication } from "@/features/apply/ApplicationContext";
import IdentityPage from "@/pages/apply/identity";
import { snapshotAt } from "../../fixtures";
import { render } from "../../render";

const replace = jest.fn();
let query: Record<string, string> = {};
jest.mock("next/router", () => ({
	useRouter: () => ({ push: jest.fn(), replace, query, asPath: "/apply/identity", pathname: "/apply/identity" }),
}));
jest.mock("@/features/apply/ApplicationContext", () => ({
	useApplication: jest.fn(),
	useStepGuard: jest.fn(),
	useApplicationSession: () => ({ signedIn: true, reload: jest.fn(), signOut: jest.fn() }),
}));

function respond(...replies: { status: number; body: unknown }[]) {
	const fetchMock = jest.fn();
	for (const reply of replies) {
		fetchMock.mockResolvedValueOnce({ ok: reply.status < 300, status: reply.status, headers: { get: () => null }, text: async () => JSON.stringify(reply.body) });
	}
	global.fetch = fetchMock as unknown as typeof fetch;
	return fetchMock;
}

describe("identity page", () => {
	beforeEach(() => {
		query = {};
		replace.mockReset();
	});

	it("opens the Aadhaar task and says what the later ones wait for", () => {
		(useApplication as jest.Mock).mockReturnValue({ snapshot: snapshotAt("AADHAAR"), write: jest.fn(), setSnapshot: jest.fn() });
		render(<IdentityPage />);
		expect(screen.getByRole("button", { name: "Continue to DigiLocker" })).toBeInTheDocument();
		expect(screen.getByText("After your Aadhaar is verified")).toBeInTheDocument();
		expect(screen.getByText("After your PAN is verified")).toBeInTheDocument();
		expect(screen.queryByRole("textbox", { name: "PAN" })).not.toBeInTheDocument();
	});

	it("sends someone without DigiLocker straight to its official sign-up page, in a new tab", () => {
		(useApplication as jest.Mock).mockReturnValue({ snapshot: snapshotAt("AADHAAR"), write: jest.fn(), setSnapshot: jest.fn() });
		render(<IdentityPage />);
		const signUp = screen.getByRole("link", { name: /Don't have a DigiLocker account\? Sign up on DigiLocker/ });
		expect(signUp).toHaveAttribute("href", "https://www.digilocker.gov.in/web/signup");
		expect(signUp).toHaveAttribute("target", "_blank");
		expect(signUp.getAttribute("rel")).toContain("noopener");
	});

	it("finishes the trip once when coming back from DigiLocker", async () => {
		query = { state: "dls_back" };
		const setSnapshot = jest.fn();
		(useApplication as jest.Mock).mockReturnValue({ snapshot: snapshotAt("AADHAAR"), write: jest.fn(), setSnapshot });
		const fetchMock = respond({ status: 200, body: snapshotAt("PAN") });
		render(<IdentityPage />);
		await waitFor(() => expect(setSnapshot).toHaveBeenCalled());
		expect(fetchMock).toHaveBeenCalledTimes(1);
		expect(JSON.parse(fetchMock.mock.calls[0][1].body)).toEqual({ state: "dls_back" });
		expect(replace).toHaveBeenCalledWith({ pathname: "/apply/identity", query: {} }, undefined, { shallow: true });
	});

	it("tells an outage apart from a failed check", () => {
		const snapshot = snapshotAt("AADHAAR");
		snapshot.steps = snapshot.steps.map((s) => (s.step_key === "AADHAAR" ? { ...s, status: "FAILED", last_error_code: "DIGILOCKER_UNAVAILABLE" } : s));
		(useApplication as jest.Mock).mockReturnValue({ snapshot, write: jest.fn(), setSnapshot: jest.fn() });
		render(<IdentityPage />);
		expect(screen.getByText(/DigiLocker is not answering right now/)).toBeInTheDocument();
		expect(screen.queryByText(/attempts left/)).not.toBeInTheDocument();
		expect(screen.getByRole("button", { name: "Start again" })).toBeInTheDocument();
	});

	it("keeps the same trip and checks again when DigiLocker is down", async () => {
		query = { state: "dls_down" };
		const down = snapshotAt("AADHAAR");
		down.steps = down.steps.map((s) => (s.step_key === "AADHAAR" ? { ...s, status: "FAILED", last_error_code: "DIGILOCKER_UNAVAILABLE" } : s));
		(useApplication as jest.Mock).mockReturnValue({ snapshot: down, write: jest.fn(), setSnapshot: jest.fn() });
		respond({ status: 202, body: down });
		render(<IdentityPage />);
		expect(await screen.findByText("Checking with DigiLocker…")).toBeInTheDocument();
		expect(screen.getByText(/DigiLocker is not answering right now/)).toBeInTheDocument();
		expect(replace).not.toHaveBeenCalled(); // the state stays in the address for the next check
	});
});

describe("voter ID or driving licence task", () => {
	it("checks the number format and sends the chosen document", async () => {
		const write = jest.fn().mockResolvedValue(snapshotAt("PHOTOS"));
		(useApplication as jest.Mock).mockReturnValue({ snapshot: snapshotAt("ALTERNATE_ID"), write, setSnapshot: jest.fn() });
		render(<IdentityPage />);
		fireEvent.change(screen.getByLabelText("Voter ID (EPIC) number"), { target: { value: "AB12" } });
		fireEvent.click(screen.getByRole("button", { name: "Verify" }));
		expect(screen.getByText(/exactly as on the card/)).toBeInTheDocument();
		expect(write).not.toHaveBeenCalled();

		fireEvent.click(screen.getByRole("radio", { name: "Driving licence" }));
		fireEvent.change(screen.getByLabelText("Driving licence number"), { target: { value: "mh01 20110012345" } });
		fireEvent.click(screen.getByRole("button", { name: "Verify" }));
		await waitFor(() =>
			expect(write).toHaveBeenCalledWith("/alternate-id-verifications", { body: { type: "DRIVING_LICENCE", number: "MH0120110012345" }, idempotent: true }),
		);
	});
});
