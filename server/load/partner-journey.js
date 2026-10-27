// k6 load test of the busiest calls: OTP send + verify, application start, snapshot read.
// Runs against a local server with the fake providers (OTP 123456):
//   make load-test            (BASE_URL defaults to http://localhost:8091)
// The thresholds are proposals until real traffic is known (docs/TESTING.md).
// All requests come from one IP: the local profile allows 1000 OTPs per IP per hour, well above the
// ~400 this run sends; against other profiles (10 per IP) it would fail by design.
import http from "k6/http";
import { check, sleep } from "k6";

const BASE = __ENV.BASE_URL || "http://localhost:8091";
const JSON_HEADERS = { "Content-Type": "application/json" };

export const options = {
	scenarios: {
		applicants: { executor: "ramping-vus", startVUs: 0, stages: [{ duration: "30s", target: 20 }, { duration: "1m", target: 20 }, { duration: "15s", target: 0 }] },
	},
	thresholds: {
		http_req_failed: ["rate<0.01"],
		"http_req_duration{name:snapshot}": ["p(95)<300"],
		"http_req_duration{name:otp_verify}": ["p(95)<500"],
	},
};

function mobile() {
	// A different 10-digit mobile per iteration, starting with 9
	return "9" + String(Math.floor(Math.random() * 1e9)).padStart(9, "0");
}

export default function () {
	const sent = http.post(`${BASE}/v1/partner/otp`, JSON.stringify({ mobile: mobile() }), { headers: JSON_HEADERS, tags: { name: "otp_send" } });
	if (!check(sent, { "otp sent": (r) => r.status === 202 })) {
		return;
	}
	const verified = http.post(`${BASE}/v1/partner/otp/verify`, JSON.stringify({ otp_ref: sent.json("otp_ref"), otp: "123456" }), {
		headers: JSON_HEADERS,
		tags: { name: "otp_verify" },
	});
	if (!check(verified, { "signed in": (r) => r.status === 200 })) {
		return;
	}
	const auth = { Authorization: `Bearer ${verified.json("access_token")}` };
	const started = http.post(`${BASE}/v1/partner/applications`, null, { headers: auth, tags: { name: "start" } });
	check(started, { started: (r) => r.status === 201 || r.status === 200 });
	for (let i = 0; i < 5; i++) {
		const snapshot = http.get(`${BASE}/v1/partner/applications/${started.json("application_id")}`, { headers: auth, tags: { name: "snapshot" } });
		check(snapshot, { snapshot: (r) => r.status === 200 });
		sleep(1);
	}
}
