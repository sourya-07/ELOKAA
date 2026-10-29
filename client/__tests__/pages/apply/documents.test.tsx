import { fireEvent, screen, waitFor, within } from "@testing-library/react";
import { useApplication } from "@/features/apply/ApplicationContext";
import { ApiError } from "@/lib/api";
import DocumentsPage from "@/pages/apply/documents";
import { snapshotAt } from "../../fixtures";
import { render } from "../../render";

const push = jest.fn();
jest.mock("next/router", () => ({ useRouter: () => ({ push, replace: jest.fn(), query: {}, asPath: "/apply/documents", pathname: "/apply/documents" }) }));
jest.mock("@/features/apply/ApplicationContext", () => ({
	useApplication: jest.fn(),
	useStepGuard: jest.fn(),
	useApplicationSession: () => ({ signedIn: true, reload: jest.fn(), signOut: jest.fn() }),
}));
let documents: { document_id: string; kind: string; status: string; file_name: string; content_type: string; size: number; uploaded_at: string }[] = [];
jest.mock("@/features/apply/useDocuments", () => ({
	useDocuments: () => ({ documents, upload: jest.fn(), remove: jest.fn(), contentUrl: () => "", ruleFor: (_kind: string, fallback: unknown) => fallback }),
}));

function withVerified(type: string) {
	const snapshot = snapshotAt("KYC_DOCUMENTS");
	snapshot.steps = snapshot.steps.map((s) => (s.step_key === "ALTERNATE_ID" ? { ...s, summary: { type, number_masked: "******4321" } } : s));
	return snapshot;
}

describe("documents page", () => {
	beforeEach(() => {
		push.mockReset();
		documents = [];
	});

	it("asks for the verified ID document, the graduation certificate, and a diploma only if there is one", () => {
		(useApplication as jest.Mock).mockReturnValue({ snapshot: withVerified("DRIVING_LICENCE"), write: jest.fn() });
		render(<DocumentsPage />);
		expect(screen.getByText("Driving licence: front")).toBeInTheDocument();
		expect(screen.queryByText("Voter ID: front")).not.toBeInTheDocument();
		const slotOf = (label: string) => screen.getByText(label).closest("div[class]")?.parentElement?.parentElement as HTMLElement;
		expect(within(slotOf("Graduation certificate")).getByText("Required")).toBeInTheDocument();
		expect(within(slotOf("Diploma certificate")).getByText("Optional")).toBeInTheDocument();
		expect(screen.queryByRole("radio")).not.toBeInTheDocument();
	});

	it("sends both groups in order, then moves on", async () => {
		const write = jest.fn().mockResolvedValueOnce(snapshotAt("EDUCATION")).mockResolvedValueOnce(snapshotAt("DECLARATION"));
		(useApplication as jest.Mock).mockReturnValue({ snapshot: withVerified("VOTER_ID"), write });
		render(<DocumentsPage />);
		fireEvent.click(screen.getByRole("button", { name: "Save and continue" }));
		await waitFor(() => expect(push).toHaveBeenCalledWith("/apply/review"));
		expect(write.mock.calls.map((c) => c[0])).toEqual(["/kyc-documents/submit", "/education/submit"]);
	});

	it("names the missing files in plain words", async () => {
		const write = jest.fn().mockRejectedValue(
			new ApiError({ status: 409, code: "DOCUMENTS_MISSING", detail: "Some required files are missing", missing_documents: ["AADHAAR_BACK", "PAN_CARD"] }),
		);
		(useApplication as jest.Mock).mockReturnValue({ snapshot: withVerified("VOTER_ID"), write });
		render(<DocumentsPage />);
		fireEvent.click(screen.getByRole("button", { name: "Save and continue" }));
		expect(await screen.findByText("Add your Aadhaar back, PAN card.")).toBeInTheDocument();
	});
});
