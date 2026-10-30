import { screen } from "@testing-library/react";
import { ApplyShell } from "@/components/ApplyShell";
import { SuccessMark } from "@/components/SuccessMark";
import { snapshotAt } from "../fixtures";
import { render } from "../render";

jest.mock("next/router", () => ({ useRouter: () => ({ push: jest.fn(), replace: jest.fn(), query: {}, asPath: "/apply/identity", pathname: "/apply/pan" }) }));

describe("apply shell", () => {
	it("shows progress as finished parts of the application, not as screens", () => {
		render(
			<ApplyShell title="Your PAN" progress={snapshotAt("PAN")}>
				<p>form</p>
			</ApplyShell>,
		);
		// mobile and consent done: contact is finished; identity is under way
		expect(screen.getByText("1 of 6 done")).toBeInTheDocument();
		expect(screen.getByText("Now: Identity")).toBeInTheDocument();
		expect(screen.getByRole("progressbar", { name: "Progress" })).toHaveAttribute("aria-valuenow", "1");
		expect(screen.getByRole("progressbar")).toHaveAttribute("aria-valuetext", "1 of 6 parts done, now Identity");
	});

	it("shows the progress on the sign-in screen too, before there is an application", () => {
		render(
			<ApplyShell title="Contact details" progress="start">
				<p>mobile</p>
			</ApplyShell>,
		);
		expect(screen.getByText("0 of 6 done")).toBeInTheDocument();
		expect(screen.getByText("Now: Contact details")).toBeInTheDocument();
		expect(screen.getByRole("progressbar")).toHaveAttribute("aria-valuenow", "0");
		// not signed in yet: no "My application" or "Sign out"
		expect(screen.queryByText("My application")).not.toBeInTheDocument();
		expect(screen.queryByRole("button", { name: "Sign out" })).not.toBeInTheDocument();
	});

	it("puts an outcome mark above the title, hidden from screen readers", () => {
		const { container } = render(
			<ApplyShell title="Application submitted" mark={<SuccessMark />}>
				<p>body</p>
			</ApplyShell>,
		);
		const mark = container.querySelector("svg[aria-hidden='true']");
		expect(mark).not.toBeNull();
		expect(mark!.compareDocumentPosition(screen.getByRole("heading", { name: "Application submitted" })) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
	});
});
