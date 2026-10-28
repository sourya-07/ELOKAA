import { fireEvent, screen } from "@testing-library/react";
import { useApplication } from "@/features/apply/ApplicationContext";
import { PanForm } from "@/features/apply/PanForm";
import { snapshotAt } from "../fixtures";
import { render } from "../render";

jest.mock("@/features/apply/ApplicationContext", () => ({ useApplication: jest.fn() }));
jest.mock("next/router", () => ({ useRouter: () => ({ push: jest.fn(), replace: jest.fn(), query: {}, pathname: "/apply/pan" }) }));

describe("PAN form", () => {
	it("shows each error under the field it is about", () => {
		const write = jest.fn();
		(useApplication as jest.Mock).mockReturnValue({ snapshot: snapshotAt("PAN"), write });
		render(<PanForm askPerson />);
		fireEvent.change(screen.getByLabelText("PAN"), { target: { value: "ABCDE1234F" } }); // not a personal PAN
		fireEvent.click(screen.getByRole("button", { name: "Verify PAN" }));

		expect(screen.getByLabelText("PAN")).toHaveAttribute("aria-invalid", "true");
		expect(screen.getByText("Enter your personal PAN, for example ABCPK1234L")).toBeInTheDocument();
		expect(screen.getByLabelText("Name as on your PAN card")).toHaveAttribute("aria-invalid", "true");
		expect(screen.getByText("Enter your name as on your PAN card")).toBeInTheDocument();
		expect(screen.getByLabelText("Date of birth")).toHaveAttribute("aria-invalid", "true");
		expect(screen.getByText("Enter your date of birth as on your PAN card")).toBeInTheDocument();
		expect(write).not.toHaveBeenCalled();
	});

	it("marks only the name when the date of birth is given", () => {
		(useApplication as jest.Mock).mockReturnValue({ snapshot: snapshotAt("PAN"), write: jest.fn() });
		render(<PanForm askPerson />);
		fireEvent.change(screen.getByLabelText("PAN"), { target: { value: "ABCPK1234L" } });
		fireEvent.change(screen.getByLabelText("Date of birth"), { target: { value: "1990-01-15" } });
		fireEvent.click(screen.getByRole("button", { name: "Verify PAN" }));
		expect(screen.getByLabelText("Name as on your PAN card")).toHaveAttribute("aria-invalid", "true");
		expect(screen.getByLabelText("Date of birth")).not.toHaveAttribute("aria-invalid");
	});
});
