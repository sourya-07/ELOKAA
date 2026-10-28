import { ChakraProvider } from "@chakra-ui/react";
import { screen } from "@testing-library/react";
import { StatusPill } from "@/components/StatusPill";
import { theme } from "@/styles/theme";
import { render } from "../render";

describe("StatusPill", () => {
	it("marks a status that has just turned to verified, so the journey can grow it in", () => {
		const { rerender } = render(<StatusPill status="VERIFICATION_IN_PROGRESS" />);
		expect(screen.getByText("verification in progress")).not.toHaveAttribute("data-turned");
		rerender(
			<ChakraProvider theme={theme}>
				<StatusPill status="VERIFIED" />
			</ChakraProvider>,
		);
		expect(screen.getByText("Verified")).toHaveAttribute("data-turned", "ok");
	});

	it("does not animate a status that was already verified when the page loaded", () => {
		render(<StatusPill status="VERIFIED" />);
		expect(screen.getByText("Verified")).not.toHaveAttribute("data-turned");
	});
});
