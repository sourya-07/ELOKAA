import { screen } from "@testing-library/react";
import { StaffShell } from "@/features/staff/StaffShell";
import { render } from "../render";

jest.mock("next/router", () => ({ useRouter: () => ({ replace: jest.fn(), push: jest.fn(), query: {} }) }));

function respond(body: unknown) {
	global.fetch = jest.fn().mockResolvedValue({ ok: true, status: 200, headers: { get: () => null }, text: async () => JSON.stringify(body) }) as unknown as typeof fetch;
}

const ME = { id: "s1", email_masked: "r***@example.com", display_name: "Sample Reviewer", roles: ["REVIEWER"], active: true };

describe("StaffShell", () => {
	it("says when the evidence comes from test providers", async () => {
		respond({ ...ME, environment: "local", test_providers: true });
		render(<StaffShell title="Applications">{() => <p>queue</p>}</StaffShell>);
		expect(await screen.findByRole("note")).toHaveTextContent("come from test providers, not real records");
	});

	it("shows no such note where the providers are real", async () => {
		respond({ ...ME, environment: "production", test_providers: false });
		render(<StaffShell title="Applications">{() => <p>queue</p>}</StaffShell>);
		expect(await screen.findByText("queue")).toBeInTheDocument();
		expect(screen.queryByRole("note")).not.toBeInTheDocument();
	});
});
