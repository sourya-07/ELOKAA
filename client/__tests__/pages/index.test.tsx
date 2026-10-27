import { screen } from "@testing-library/react";
import Home from "@/pages/index";
import { render } from "../render";

describe("Home", () => {
	it("shows the heading and the way in", () => {
		render(<Home />);
		expect(screen.getByRole("heading", { name: "Become an ELOKA partner" })).toBeInTheDocument();
		expect(screen.getByRole("link", { name: "Start your application" })).toHaveAttribute("href", "/apply");
	});
});
