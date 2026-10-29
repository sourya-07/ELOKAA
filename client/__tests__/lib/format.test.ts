import { clock, waitText } from "@/lib/format";

describe("clock", () => {
	it("shows waits as minutes and seconds", () => {
		expect(clock(30)).toBe("0:30");
		expect(clock(90)).toBe("1:30");
		expect(clock(900)).toBe("15:00");
		expect(clock(-3)).toBe("0:00");
	});
});

describe("waitText", () => {
	it("says a minute or more in minutes, never less than the server's wait", () => {
		expect(waitText(60)).toBe("1 min");
		expect(waitText(61)).toBe("2 min");
		expect(waitText(900)).toBe("15 min");
		expect(waitText(59)).toBe("59 s");
		expect(waitText(0.4)).toBe("1 s");
		expect(waitText(-2)).toBe("0 s");
	});
});
