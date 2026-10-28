import { ChakraProvider } from "@chakra-ui/react";
import { fireEvent, screen } from "@testing-library/react";
import { TaskSection } from "@/components/TaskSection";
import { theme } from "@/styles/theme";
import { render } from "../render";

describe("TaskSection", () => {
	it("opens the task to do now", () => {
		render(
			<TaskSection id="pan" title="PAN" state="current">
				<p>pan form</p>
			</TaskSection>,
		);
		expect(screen.getByText("pan form")).toBeInTheDocument();
		expect(screen.getByRole("status")).toHaveTextContent("To do now");
	});

	it("folds a done task to its summary and can open it again", () => {
		render(
			<TaskSection id="pan" title="PAN" state="done" summary="ABCPK****L" canChange>
				<p>pan form</p>
			</TaskSection>,
		);
		expect(screen.getByText("ABCPK****L")).toBeInTheDocument();
		expect(screen.queryByText("pan form")).not.toBeInTheDocument();
		fireEvent.click(screen.getByRole("button", { name: "Change" }));
		expect(screen.getByText("pan form")).toBeInTheDocument();
		expect(screen.getByRole("heading", { name: "PAN" })).toHaveFocus();
	});

	it("says why a later task waits", () => {
		render(
			<TaskSection id="credit" title="Eligibility" state="locked" lockedReason="After your PAN is verified">
				<p>credit</p>
			</TaskSection>,
		);
		expect(screen.getByText("After your PAN is verified")).toBeInTheDocument();
		expect(screen.queryByText("credit")).not.toBeInTheDocument();
		expect(screen.queryByRole("button")).not.toBeInTheDocument();
	});

	it("animates only a change: the mark that turns to done, not one already done", () => {
		const { rerender } = render(
			<TaskSection id="pan" title="PAN" state="current">
				<p>pan form</p>
			</TaskSection>,
		);
		expect(screen.getByRole("status")).not.toHaveAttribute("data-turned");
		rerender(
			<ChakraProvider theme={theme}>
				<TaskSection id="pan" title="PAN" state="done" summary="ABCPK****L">
					<p>pan form</p>
				</TaskSection>
			</ChakraProvider>,
		);
		expect(screen.getByRole("status")).toHaveTextContent("Done");
		expect(screen.getByRole("status")).toHaveAttribute("data-turned", "ok");
	});
});
