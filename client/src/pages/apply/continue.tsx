import { Center, Spinner } from "@chakra-ui/react";
import { useRouter } from "next/router";
import { useEffect } from "react";
import { useApplication } from "@/features/apply/ApplicationContext";
import { withApplication } from "@/features/apply/withApplication";
import { routeFor } from "@/lib/steps";

/** After sign-in: open (or start) the application and go to where it continues. */
function Continue() {
	const { snapshot } = useApplication();
	const router = useRouter();
	useEffect(() => {
		router.replace(routeFor(snapshot));
	}, [router, snapshot]);
	return (
		<Center minH="60vh">
			<Spinner label="Opening your application" />
		</Center>
	);
}

export default withApplication(Continue);
