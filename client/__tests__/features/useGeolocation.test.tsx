import { act, renderHook } from "@testing-library/react";
import { useGeolocation } from "@/features/apply/useGeolocation";

type Success = (position: GeolocationPosition) => void;
type Failure = (error: GeolocationPositionError) => void;

function fakeGeolocation() {
	const handlers: { success?: Success; failure?: Failure } = {};
	const geolocation = {
		watchPosition: jest.fn((success: Success, failure: Failure) => {
			handlers.success = success;
			handlers.failure = failure;
			return 7;
		}),
		clearWatch: jest.fn(),
	};
	Object.defineProperty(navigator, "geolocation", { value: geolocation, configurable: true });
	const position = (latitude: number, accuracy: number) =>
		({ coords: { latitude, longitude: 72.8, accuracy }, timestamp: Date.now() }) as unknown as GeolocationPosition;
	return { geolocation, handlers, position };
}

describe("useGeolocation", () => {
	it("starts only when asked and keeps the most precise recent fix", () => {
		const { geolocation, handlers, position } = fakeGeolocation();
		const { result } = renderHook(() => useGeolocation());
		expect(result.current.state.kind).toBe("idle");
		expect(geolocation.watchPosition).not.toHaveBeenCalled();

		act(() => result.current.start());
		expect(result.current.state.kind).toBe("locating");
		act(() => handlers.success?.(position(19.05, 400)));
		act(() => handlers.success?.(position(19.06, 15)));
		act(() => handlers.success?.(position(19.07, 900))); // rougher: ignored
		expect(result.current.current()).toMatchObject({ latitude: 19.06, accuracy: 15 });
		expect(result.current.state).toMatchObject({ kind: "ready" });
	});

	it("tells denied, unavailable and timeout apart, and never invents a fix", () => {
		for (const [code, kind] of [[1, "denied"], [2, "unavailable"], [3, "timeout"]] as const) {
			const { handlers } = fakeGeolocation();
			const { result } = renderHook(() => useGeolocation());
			act(() => result.current.start());
			act(() => handlers.failure?.({ code } as GeolocationPositionError));
			expect(result.current.state.kind).toBe(kind);
			expect(result.current.current()).toBeNull();
		}
	});

	it("says when the browser has no location service", () => {
		Object.defineProperty(navigator, "geolocation", { value: undefined, configurable: true });
		const { result } = renderHook(() => useGeolocation());
		act(() => result.current.start());
		expect(result.current.state.kind).toBe("unsupported");
	});
});
