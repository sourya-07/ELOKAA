import { ChakraProvider } from "@chakra-ui/react";
import { render, screen } from "@testing-library/react";
import Home from "@/pages/index";

describe("Home", () => {
	it("shows the page heading", () => {
		render(
			<ChakraProvider>
				<Home />
			</ChakraProvider>,
		);
		expect(screen.getByRole("heading", { name: "Become an ELOKA partner" })).toBeInTheDocument();
	});
});
