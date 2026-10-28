import { Button, FormControl, FormErrorMessage, FormLabel, Input, Select, SimpleGrid, Stack } from "@chakra-ui/react";
import { type ChangeEvent, useState } from "react";
import { ErrorBanner } from "@/components/ErrorBanner";
import { ApiError } from "@/lib/api";
import { STATES } from "@/lib/states";
import { isPincode } from "@/lib/validation";
import { useApplication } from "./ApplicationContext";
import { useAction } from "./useAction";

type Field = "line1" | "line2" | "city" | "district" | "state" | "pincode";

/**
 * Home address typed by the applicant: only when DigiLocker did not give a whole one, or a reviewer
 * asked for a correction (the server refuses it otherwise).
 */
export function AddressForm() {
	const { write } = useApplication();
	const [values, setValues] = useState<Record<Field, string>>({ line1: "", line2: "", city: "", district: "", state: "", pincode: "" });
	const [errors, setErrors] = useState<Partial<Record<Field, string>>>({});
	const { busy, error, run } = useAction();

	const set = (field: Field) => (event: ChangeEvent<HTMLInputElement | HTMLSelectElement>) => setValues({ ...values, [field]: event.target.value });

	const submit = async () => {
		const found: Partial<Record<Field, string>> = {};
		if (!values.line1.trim()) found.line1 = "Enter your house and street";
		if (!values.city.trim()) found.city = "Enter your city or village";
		if (!values.state) found.state = "Choose your state";
		if (!isPincode(values.pincode)) found.pincode = "Enter a valid 6-digit pincode";
		setErrors(found);
		if (Object.keys(found).length) {
			return;
		}
		await run(async () => {
			try {
				await write("/home-address", { method: "PUT", body: values });
			} catch (e) {
				if (e instanceof ApiError && e.problem.invalid_params) {
					setErrors(e.problem.invalid_params as Partial<Record<Field, string>>);
				}
				throw e;
			}
		});
	};

	const field = (name: Field, label: string, props: Record<string, unknown> = {}) => (
		<FormControl isInvalid={!!errors[name]} isRequired={name !== "line2" && name !== "district"}>
			<FormLabel htmlFor={name}>{label}</FormLabel>
			<Input id={name} value={values[name]} onChange={set(name)} {...props} />
			<FormErrorMessage>{errors[name]}</FormErrorMessage>
		</FormControl>
	);

	return (
		<Stack spacing={4}>
			{field("line1", "House and street", { autoComplete: "address-line1" })}
			{field("line2", "Area or landmark (optional)", { autoComplete: "address-line2" })}
			<SimpleGrid columns={{ base: 1, md: 2 }} spacing={4}>
				{field("city", "City or village", { autoComplete: "address-level2" })}
				{field("district", "District (optional)")}
			</SimpleGrid>
			<SimpleGrid columns={{ base: 1, md: 2 }} spacing={4}>
				<FormControl isInvalid={!!errors.state} isRequired>
					<FormLabel htmlFor="state">State</FormLabel>
					<Select id="state" placeholder="Choose your state" value={values.state} onChange={set("state")}>
						{STATES.map((state) => (
							<option key={state}>{state}</option>
						))}
					</Select>
					<FormErrorMessage>{errors.state}</FormErrorMessage>
				</FormControl>
				{field("pincode", "Pincode", { inputMode: "numeric", maxLength: 6, autoComplete: "postal-code" })}
			</SimpleGrid>
			<ErrorBanner message={error} />
			<Button onClick={submit} isLoading={busy} alignSelf="flex-start">
				Save address
			</Button>
		</Stack>
	);
}
