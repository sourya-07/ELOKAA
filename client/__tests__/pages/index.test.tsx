import { screen } from "@testing-library/react";
import Home from "@/pages/index";
import { render } from "../render";

describe("Home", () => {
	it("shows the heading and the way in", () => {
		render(<Home />);
		expect(screen.getByRole("heading", { name: "Become an ELOKA partner" })).toBeInTheDocument();
		for (const link of screen.getAllByRole("link", { name: "Start your application" })) {
			expect(link).toHaveAttribute("href", "/apply");
		}
	});

	it("states the credit rule before anyone starts", () => {
		render(<Home />);
		expect(screen.getByText("Your CIBIL credit score is 700 or more.")).toBeInTheDocument();
		expect(screen.getByText(/below 700 you are not eligible/)).toBeInTheDocument();
		expect(screen.getByRole("heading", { name: "How it works" })).toBeInTheDocument();
	});
});
