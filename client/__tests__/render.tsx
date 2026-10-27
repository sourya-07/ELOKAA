import { ChakraProvider } from "@chakra-ui/react";
import { render as rtlRender } from "@testing-library/react";
import type { ReactElement } from "react";
import { theme } from "@/styles/theme";

export function render(ui: ReactElement) {
	return rtlRender(<ChakraProvider theme={theme}>{ui}</ChakraProvider>);
}
