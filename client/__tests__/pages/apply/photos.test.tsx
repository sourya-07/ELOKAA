import { act, fireEvent, screen } from "@testing-library/react";
import { useApplication } from "@/features/apply/ApplicationContext";
import PhotosPage from "@/pages/apply/photos";
import { snapshotAt } from "../../fixtures";
import { render } from "../../render";

const upload = jest.fn();
jest.mock("next/router", () => ({ useRouter: () => ({ push: jest.fn(), replace: jest.fn(), query: {}, asPath: "/apply/photos", pathname: "/apply/photos" }) }));
jest.mock("@/features/apply/ApplicationContext", () => ({
	useApplication: jest.fn(),
	useStepGuard: jest.fn(),
	useApplicationSession: () => ({ signedIn: true, reload: jest.fn(), signOut: jest.fn() }),
}));
jest.mock("@/features/apply/useDocuments", () => ({
	useDocuments: () => ({ documents: [], upload, remove: jest.fn(), contentUrl: () => "" }),
}));

describe("photos page", () => {
	it("cannot send the live photo without a location, and says how to allow it", () => {
		let fail: ((error: GeolocationPositionError) => void) | undefined;
		Object.defineProperty(navigator, "geolocation", {
			value: { watchPosition: jest.fn((_ok, error) => ((fail = error), 1)), clearWatch: jest.fn() },
			configurable: true,
		});
		Object.defineProperty(navigator, "mediaDevices", { value: undefined, configurable: true }); // phone-camera fallback
		(useApplication as jest.Mock).mockReturnValue({ snapshot: snapshotAt("PHOTOS"), write: jest.fn(), refresh: jest.fn() });
		const { container } = render(<PhotosPage />);

		fireEvent.click(screen.getByRole("button", { name: "Open camera" }));
		act(() => fail?.({ code: 1 } as GeolocationPositionError));

		expect(screen.getByText(/Location is blocked for this site/)).toBeInTheDocument();
		expect(container.querySelector("input[type=file][capture]")).toBeDisabled();
		expect(upload).not.toHaveBeenCalled();
	});
});
