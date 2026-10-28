import { clock } from "@/lib/format";

describe("clock", () => {
	it("shows waits as minutes and seconds", () => {
		expect(clock(30)).toBe("0:30");
		expect(clock(90)).toBe("1:30");
		expect(clock(900)).toBe("15:00");
		expect(clock(-3)).toBe("0:00");
	});
});
