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
});
