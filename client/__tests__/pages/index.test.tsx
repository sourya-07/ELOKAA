import { screen } from "@testing-library/react";
import Home from "@/pages/index";
import { render } from "../render";

describe("Home", () => {
	it("shows the heading and the way in", () => {
		render(<Home />);
		expect(screen.getByRole("heading", { name: "Become an ELOKA partner" })).toBeInTheDocument();
		expect(screen.getByRole("link", { name: "Start your application" })).toHaveAttribute("href", "/apply");
	});

	it("states the credit rule before anyone starts", () => {
		render(<Home />);
		expect(screen.getByText("Your CIBIL credit score is 700 or more.")).toBeInTheDocument();
		// the rule, and nothing more about what happens below it
		expect(screen.queryByText(/below 700/)).not.toBeInTheDocument();
		expect(screen.getByRole("heading", { name: "How it works" })).toBeInTheDocument();
	});

	it("asks for soft copies and states the real file limits", () => {
		render(<Home />);
		expect(screen.getByText("Keep your documents ready in digital format (soft copies).")).toBeInTheDocument();
		expect(screen.getByText("Photos: JPG or PNG, 20 KB to 2 MB. Documents: JPG, PNG or PDF, 10 KB to 5 MB.")).toBeInTheDocument();
	});
});
