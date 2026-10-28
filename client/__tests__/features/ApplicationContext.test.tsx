import { act, fireEvent, screen, waitFor } from "@testing-library/react";
import { useState } from "react";
import { ApplicationProvider, useApplication } from "@/features/apply/ApplicationContext";
import { snapshotAt } from "../fixtures";
import { render } from "../render";

const replace = jest.fn();
jest.mock("next/router", () => ({
	// a new object on every call, like the Pages Router
	useRouter: () => ({ push: jest.fn(), replace, query: {}, asPath: "/apply/identity", pathname: "/apply/pan" }),
}));

function reply(status: number, body: unknown) {
	return { ok: status < 300, status, headers: { get: () => null }, text: async () => JSON.stringify(body) };
}

function Probe() {
	const { snapshot, write, setSnapshot } = useApplication();
	const [, rerender] = useState(0);
	return (
		<>
			<p>version {snapshot.version}</p>
			<button onClick={() => write("/pan-verifications", { body: { pan: "ABCPK1234L" }, idempotent: true }).catch(() => undefined)}>first</button>
			<button onClick={() => write("/pan-verifications", { body: { pan: "ABCPK9999L" }, idempotent: true }).catch(() => undefined)}>second</button>
			<button onClick={() => setSnapshot(snapshotAt("PAN", { version: 1 }))}>stale</button>
			<button onClick={() => rerender((n) => n + 1)}>rerender</button>
		</>
	);
}

describe("application provider", () => {
	it("loads once, not again on every render", async () => {
		const fetchMock = jest.fn().mockResolvedValue(reply(200, snapshotAt("PAN")));
		global.fetch = fetchMock as unknown as typeof fetch;
		render(
			<ApplicationProvider>
				<Probe />
			</ApplicationProvider>,
		);
		await screen.findByText("version 3");
		fireEvent.click(screen.getByText("rerender"));
		fireEvent.click(screen.getByText("rerender"));
		expect(fetchMock).toHaveBeenCalledTimes(1);
	});

	it("uses a new idempotency key when the body changes, and ignores an older snapshot", async () => {
		const fetchMock = jest
			.fn()
			.mockResolvedValueOnce(reply(200, snapshotAt("PAN")))
			.mockResolvedValueOnce(reply(422, { code: "VALIDATION_FAILED" }))
			.mockResolvedValueOnce(reply(422, { code: "VALIDATION_FAILED" }));
		global.fetch = fetchMock as unknown as typeof fetch;
		render(
			<ApplicationProvider>
				<Probe />
			</ApplicationProvider>,
		);
		await screen.findByText("version 3");
		fireEvent.click(screen.getByText("first"));
		await waitFor(() => expect(fetchMock).toHaveBeenCalledTimes(2));
		fireEvent.click(screen.getByText("second"));
		await waitFor(() => expect(fetchMock).toHaveBeenCalledTimes(3));
		const key = (n: number) => (fetchMock.mock.calls[n][1].headers as Record<string, string>)["idempotency-key"];
		expect(key(1)).toBeTruthy();
		expect(key(2)).not.toBe(key(1));

		act(() => fireEvent.click(screen.getByText("stale")));
		expect(screen.getByText("version 3")).toBeInTheDocument();
	});

	it("forgets the application when the session has ended", async () => {
		const fetchMock = jest
			.fn()
			.mockResolvedValueOnce(reply(200, snapshotAt("PAN")))
			.mockResolvedValueOnce(reply(401, { code: "UNAUTHENTICATED" }));
		global.fetch = fetchMock as unknown as typeof fetch;
		render(
			<ApplicationProvider>
				<Probe />
			</ApplicationProvider>,
		);
		await screen.findByText("version 3");
		fireEvent.click(screen.getByText("first"));
		await waitFor(() => expect(replace).toHaveBeenCalledWith("/apply"));
		expect(screen.queryByText("version 3")).not.toBeInTheDocument();
	});
});
