import { ChakraProvider } from "@chakra-ui/react";
import { fireEvent, screen, waitFor } from "@testing-library/react";
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

	it("folds again once a save gives a new version, showing the saved result", async () => {
		const { rerender } = render(
			<TaskSection id="address" title="Home address" state="done" summary="Bihar 800001" canChange version={4}>
				<p>address form</p>
			</TaskSection>,
		);
		fireEvent.click(screen.getByRole("button", { name: "Change" }));
		expect(screen.getByText("address form")).toBeInTheDocument();
		rerender(
			<ChakraProvider theme={theme}>
				<TaskSection id="address" title="Home address" state="done" summary="Bihar 800002" canChange version={5}>
					<p>address form</p>
				</TaskSection>
			</ChakraProvider>,
		);
		await waitFor(() => expect(screen.queryByText("address form")).not.toBeInTheDocument());
		expect(screen.getByText("Bihar 800002")).toBeInTheDocument();
	});

	it("shows a status as a mark and words, never colour alone", () => {
		render(
			<TaskSection id="pan" title="PAN" state="done" summary="ABCPK****L">
				<p>pan form</p>
			</TaskSection>,
		);
		const status = screen.getByRole("status");
		expect(status).toHaveTextContent("Done");
		expect(status.querySelector("svg[data-tone=ok]")).not.toBeNull();
		// a verified task without canChange offers no "Change"
		expect(screen.queryByRole("button", { name: "Change" })).not.toBeInTheDocument();
	});
});
