import { screen } from "@testing-library/react";
import { ApplyShell } from "@/components/ApplyShell";
import { SuccessMark } from "@/components/SuccessMark";
import { render } from "../render";

describe("apply shell", () => {
	it("shows the step and an accessible progress line", () => {
		render(
			<ApplyShell title="Your PAN" step={{ index: 2, total: 8 }}>
				<p>form</p>
			</ApplyShell>,
		);
		expect(screen.getByText("Step 2 of 8")).toBeInTheDocument();
		expect(screen.getByRole("progressbar", { name: "Progress" })).toHaveAttribute("aria-valuenow", "25");
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
