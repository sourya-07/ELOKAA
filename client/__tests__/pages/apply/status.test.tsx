import { screen } from "@testing-library/react";
import { useApplication } from "@/features/apply/ApplicationContext";
import StatusPage from "@/pages/apply/status";
import { snapshotAt } from "../../fixtures";
import { render } from "../../render";

jest.mock("next/router", () => ({ useRouter: () => ({ push: jest.fn(), replace: jest.fn(), query: {}, asPath: "/apply/status", pathname: "/apply/status" }) }));
jest.mock("@/features/apply/ApplicationContext", () => ({
	useApplication: jest.fn(),
	useStepGuard: jest.fn(),
	useApplicationSession: () => ({ signedIn: true, reload: jest.fn(), signOut: jest.fn() }),
}));

describe("status page", () => {
	it("shows the 10-digit application number, not the internal id", () => {
		(useApplication as jest.Mock).mockReturnValue({ snapshot: snapshotAt(null, { status: "SUBMITTED", submitted_at: "2026-10-29T10:00:00Z" }), write: jest.fn() });
		render(<StatusPage />);
		expect(screen.getByText("Application number")).toBeInTheDocument();
		expect(screen.getByText("1000000042")).toBeInTheDocument();
		expect(screen.queryByText("ptn_TEST")).not.toBeInTheDocument();
	});

	it("shows the masked mobile and email and promises no message the system does not send", () => {
		(useApplication as jest.Mock).mockReturnValue({ snapshot: snapshotAt(null, { status: "SUBMITTED" }), write: jest.fn() });
		render(<StatusPage />);
		expect(screen.getByText("+91 ******3210")).toBeInTheDocument();
		expect(screen.getByText("r***@example.com")).toBeInTheDocument();
		expect(screen.queryByText(/on your mobile/)).not.toBeInTheDocument();
		expect(screen.queryByText(/We tell you/)).not.toBeInTheDocument();
	});

	it("shows the progress line here too", () => {
		(useApplication as jest.Mock).mockReturnValue({ snapshot: snapshotAt(null, { status: "SUBMITTED" }), write: jest.fn() });
		render(<StatusPage />);
		expect(screen.getByRole("progressbar", { name: "Progress" })).toBeInTheDocument();
		expect(screen.queryByText("My application")).not.toBeInTheDocument(); // no link to the page itself
	});
});
