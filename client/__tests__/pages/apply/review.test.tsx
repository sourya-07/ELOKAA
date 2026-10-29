import { fireEvent, screen, waitFor } from "@testing-library/react";
import { useApplication } from "@/features/apply/ApplicationContext";
import ReviewPage from "@/pages/apply/review";
import { snapshotAt } from "../../fixtures";
import { render } from "../../render";

const push = jest.fn();
jest.mock("next/router", () => ({ useRouter: () => ({ push, replace: jest.fn(), query: {}, asPath: "/apply/review", pathname: "/apply/review" }) }));
jest.mock("@/features/apply/ApplicationContext", () => ({
	useApplication: jest.fn(),
	useStepGuard: jest.fn(),
	useApplicationSession: () => ({ signedIn: true, reload: jest.fn(), signOut: jest.fn() }),
}));

describe("review page", () => {
	beforeEach(() => push.mockReset());

	it("lists what still stops submission, each with a way to fix it", () => {
		const snapshot = snapshotAt("KYC_DOCUMENTS", {
			submit_blockers: [
				{ step_key: "KYC_DOCUMENTS", code: "DOCUMENTS_MISSING", group: "PAN_CARD" },
				{ step_key: "EDUCATION", code: "DOCUMENTS_MISSING", group: "GRADUATION_OR_DIPLOMA" },
			],
		});
		(useApplication as jest.Mock).mockReturnValue({ snapshot, write: jest.fn() });
		render(<ReviewPage />);
		expect(screen.getByText(/Add your PAN card\./)).toBeInTheDocument();
		expect(screen.getByText(/Add your graduation or diploma certificate\./)).toBeInTheDocument();
		const fixes = screen.getAllByRole("link", { name: "Fix" });
		expect(fixes[0]).toHaveAttribute("href", "/apply/documents?return=review#identity-documents");
		expect(screen.getByRole("button", { name: "Submit application" })).toBeDisabled();
	});

	it("asks once more, then submits once and shows the status", async () => {
		const write = jest.fn().mockResolvedValue(snapshotAt(null, { status: "SUBMITTED" }));
		(useApplication as jest.Mock).mockReturnValue({ snapshot: snapshotAt("DECLARATION", { submit_blockers: [{ step_key: "DECLARATION", code: "STEP_NOT_DONE" }] }), write });
		render(<ReviewPage />);
		fireEvent.click(screen.getByRole("button", { name: "Submit application" }));
		expect(screen.getByText("Confirm the declaration to submit")).toBeInTheDocument();
		fireEvent.click(screen.getByRole("checkbox"));
		fireEvent.click(screen.getByRole("button", { name: "Submit application" }));
		expect(await screen.findByText("Submit your application?")).toBeInTheDocument();
		expect(write).not.toHaveBeenCalled();
		fireEvent.click(screen.getByRole("button", { name: "Submit" }));
		await waitFor(() => expect(push).toHaveBeenCalledWith("/apply/status"));
		expect(write.mock.calls.map((c) => c[0])).toEqual(["/declaration", "/submit"]);
		expect(write.mock.calls[1][1]).toEqual({ idempotent: true });
	});

	it("keeps verified parts read-only, says where each came from, and links back to the right place", () => {
		const snapshot = snapshotAt("DECLARATION");
		snapshot.steps = snapshot.steps.map((s) =>
			s.step_key === "EMAIL_VERIFICATION" ? { ...s, summary: { email_masked: "r***@example.com" } } : s.step_key === "PAN" ? { ...s, summary: { pan_masked: "ABCPK****L" } } : s.step_key === "HOME_ADDRESS" ? { ...s, summary: { state: "Bihar", pincode: "800001", source: "DIGILOCKER" } } : s,
		);
		(useApplication as jest.Mock).mockReturnValue({ snapshot, write: jest.fn() });
		render(<ReviewPage />);
		const changes = screen.getAllByRole("link", { name: "Change" }).map((link) => link.getAttribute("href"));
		expect(changes).toContain("/apply?return=review");
		expect(changes).toContain("/apply/documents?return=review#identity-documents");
		expect(changes.some((href) => href?.includes("#pan") || href?.includes("#aadhaar") || href?.includes("#address"))).toBe(false);
		expect(screen.getAllByText("Verified with the government record").length).toBeGreaterThan(0);
		expect(screen.getByText("From DigiLocker")).toBeInTheDocument();
		expect(screen.getByText(/Mobile \+91 \*{6}3210/)).toBeInTheDocument();
	});
});
