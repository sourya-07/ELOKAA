import { Button, FormControl, FormErrorMessage, FormLabel, Input, Select, SimpleGrid } from "@chakra-ui/react";
import { useRouter } from "next/router";
import { useState } from "react";
import { ApplyShell, Section } from "@/components/ApplyShell";
import { ErrorBanner } from "@/components/ErrorBanner";
import { useApplication, useStepGuard } from "@/features/apply/ApplicationContext";
import { canOpen } from "@/features/apply/guards";
import { useAction } from "@/features/apply/useAction";
import { withApplication } from "@/features/apply/withApplication";
import { ApiError } from "@/lib/api";
import { STATES } from "@/lib/states";
import { position, routeFor } from "@/lib/steps";
import { isPincode } from "@/lib/validation";

type Field = "line1" | "line2" | "city" | "district" | "state" | "pincode";

/** Home address, typed by the applicant. */
function AddressPage() {
	const { snapshot, write } = useApplication();
	const router = useRouter();
	useStepGuard((s) => canOpen(s, "HOME_ADDRESS"));
	const [values, setValues] = useState<Record<Field, string>>({ line1: "", line2: "", city: "", district: "", state: "", pincode: "" });
	const [errors, setErrors] = useState<Partial<Record<Field, string>>>({});
	const { busy, error, run } = useAction();

	const set = (field: Field) => (event: React.ChangeEvent<HTMLInputElement | HTMLSelectElement>) =>
		setValues({ ...values, [field]: event.target.value });

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
				const next = await write("/home-address", { method: "PUT", body: values });
				await router.push(routeFor(next));
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
		<ApplyShell title="Home address" subtitle="Where you live now." step={position(snapshot.method, "/apply/address")}>
			<Section>
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
			</Section>
			<ErrorBanner message={error} />
			<Button onClick={submit} isLoading={busy}>
				Continue
			</Button>
		</ApplyShell>
	);
}

export default withApplication(AddressPage);
