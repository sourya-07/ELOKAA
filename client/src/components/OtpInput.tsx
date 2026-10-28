import { HStack, Input } from "@chakra-ui/react";
import { useRef } from "react";

interface Props {
	length?: number;
	value: string;
	onChange: (value: string) => void;
	isInvalid?: boolean;
	isDisabled?: boolean;
}

/** One box per digit, with paste and backspace across boxes. Each box has its own label for screen readers. */
export function OtpInput({ length = 6, value, onChange, isInvalid, isDisabled }: Props) {
	const refs = useRef<(HTMLInputElement | null)[]>([]);
	const digits = Array.from({ length }, (_, i) => value[i] ?? "");

	const set = (index: number, digit: string) => {
		const next = digits.slice();
		next[index] = digit;
		onChange(next.join("").slice(0, length));
	};

	return (
		<HStack spacing={2} role="group" aria-label="One-time code">
			{digits.map((digit, index) => (
				<Input
					key={index}
					ref={(el) => {
						refs.current[index] = el;
					}}
					value={digit}
					aria-label={`Digit ${index + 1} of ${length}`}
					inputMode="numeric"
					spellCheck={false}
					autoComplete={index === 0 ? "one-time-code" : "off"}
					textAlign="center"
					w="48px"
					px={0}
					isInvalid={isInvalid}
					isDisabled={isDisabled}
					onChange={(event) => {
						// No maxLength: SMS autofill puts the whole code into one box and must reach this handler
						const typed = event.target.value.replace(/\D/g, "");
						const overwrite = typed.length === 2 && digit !== "";
						if (typed.length > 1 && !overwrite) {
							onChange(typed.slice(0, length));
							refs.current[Math.min(typed.length, length) - 1]?.focus();
							return;
						}
						// Typing over a filled box: keep the new digit, not the old one
						const next = overwrite ? (typed[0] === digit ? typed[1] : typed[0]) : typed;
						set(index, next);
						if (next && index < length - 1) {
							refs.current[index + 1]?.focus();
						}
					}}
					onKeyDown={(event) => {
						if (event.key === "Backspace" && !digit && index > 0) {
							refs.current[index - 1]?.focus();
						}
					}}
					onPaste={(event) => {
						const pasted = event.clipboardData.getData("text").replace(/\D/g, "").slice(0, length);
						if (pasted) {
							event.preventDefault();
							onChange(pasted);
							refs.current[Math.min(pasted.length, length) - 1]?.focus();
						}
					}}
				/>
			))}
		</HStack>
	);
}
