# Server API

Only what is built. Base path `/v1`. Errors are RFC 9457 `application/problem+json` with a stable `code`
(and `invalid_params`, `retry_after`, `attempts_left` where relevant). JSON fields are snake_case.
Callers are the client's server-side proxy (ADR-0003), not browsers.

## Applicant sign-in (`identity` module)

| Method + path | Auth | Body | Success | Errors |
|---|---|---|---|---|
| `POST /v1/partner/otp` | public | `{"mobile"}` (10 digits, `+91`/`0` accepted) | `202 {"otp_ref","expires_in","resend_after","otp_length"}` | `422 VALIDATION_FAILED`, `429 OTP_LIMIT_REACHED` (+`retry_after` s), `409 PROVIDER_NOT_CONFIGURED`, `503 PROVIDER_UNAVAILABLE` |
| `POST /v1/partner/otp/verify` | public | `{"otp_ref","otp"}` | `200 {"access_token","refresh_token","token_type":"Bearer","expires_in"}` | `422 OTP_INVALID` (+`attempts_left`), `429 OTP_LOCKED`, `410 OTP_EXPIRED`, `422 VALIDATION_FAILED` (bad `otp_ref`), `503 PROVIDER_UNAVAILABLE` |
| `POST /v1/partner/session/refresh` | public | `{"refresh_token"}` | `200` new token pair (both rotate) | `401 SESSION_EXPIRED` (a reused old refresh token also ends the session) |
| `DELETE /v1/partner/session` | applicant | — | `204` | `401 UNAUTHENTICATED` |

Rules: answers are the same for new and known mobiles; the first verified OTP creates the applicant.
Limits (defaults, `eloka.identity.*`): OTP valid 5 min, 5 wrong tries, 30 s between sends, 5 per hour and
10 per day per mobile, 10 per hour per IP. Sessions: access token 30 min, idle 2 h, absolute 12 h.

## Operations

| Path | Auth | Note |
|---|---|---|
| `GET /actuator/health`, `/actuator/health/{liveness,readiness}`, `/actuator/info` | public | management port 8081 in the `prod` profile |
