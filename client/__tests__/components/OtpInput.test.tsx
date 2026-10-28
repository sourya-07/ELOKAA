import { fireEvent, screen } from "@testing-library/react";
import { useState } from "react";
import { OtpInput } from "@/components/OtpInput";
import { render } from "../render";

function Harness({ onValue }: { onValue: (value: string) => void }) {
	const [value, setValue] = useState("");
	return (
		<OtpInput
			value={value}
			onChange={(next) => {
				setValue(next);
				onValue(next);
			}}
		/>
	);
}

describe("OtpInput", () => {
	it("labels every box for screen readers", () => {
		render(<Harness onValue={() => undefined} />);
		expect(screen.getAllByRole("textbox")).toHaveLength(6);
		expect(screen.getByLabelText("Digit 1 of 6")).toBeInTheDocument();
	});

	it("takes digits only and fills from a paste", () => {
		const onValue = jest.fn();
		render(<Harness onValue={onValue} />);
		fireEvent.change(screen.getByLabelText("Digit 1 of 6"), { target: { value: "a" } });
		expect(onValue).toHaveBeenLastCalledWith("");
		fireEvent.paste(screen.getByLabelText("Digit 1 of 6"), { clipboardData: { getData: () => "12 34 56" } });
		expect(onValue).toHaveBeenLastCalledWith("123456");
	});

	it("accepts SMS autofill of the whole code into the first box", () => {
		const onValue = jest.fn();
		render(<Harness onValue={onValue} />);
		fireEvent.change(screen.getByLabelText("Digit 1 of 6"), { target: { value: "654321" } });
		expect(onValue).toHaveBeenLastCalledWith("654321");
	});

	it("replaces a digit when typing over a filled box", () => {
		const onValue = jest.fn();
		render(<Harness onValue={onValue} />);
		fireEvent.change(screen.getByLabelText("Digit 1 of 6"), { target: { value: "4" } });
		fireEvent.change(screen.getByLabelText("Digit 1 of 6"), { target: { value: "47" } });
		expect(onValue).toHaveBeenLastCalledWith("7");
	});
});
