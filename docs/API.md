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

## Application (`onboarding` module)

All routes need an applicant session. Every answer is the application snapshot with `ETag: "v<version>"`;
every change needs `If-Match` (`428 PRECONDITION_REQUIRED` without it, `412 PRECONDITION_FAILED` with the
current snapshot when stale). Another applicant's id answers `404 APPLICATION_NOT_FOUND`.

| Method + path | Body | Success | Errors |
|---|---|---|---|
| `POST /v1/partner/applications` | — | `201` new, `200` resumed (open or ineligible application) | `409 ALREADY_APPROVED` |
| `GET /v1/partner/applications/current` | — | `200` latest application | `404 NO_APPLICATION` |
| `GET /v1/partner/applications/{id}` | — | `200` | `404` |
| `GET /v1/partner/applications/{id}/timeline` | — | `200 [{"event_type","step_key","to_status","occurred_at"}]` | `404` |
| `POST /v1/partner/applications/{id}/consents` | `{"purpose": DATA_PROCESSING \| DIGILOCKER \| CREDIT_CHECK, "version"}` | `200` (data-use consent completes `CONSENT`) | `409 CONSENT_VERSION_OUTDATED` (+`current_version`), `409 PREREQUISITE_INCOMPLETE` |
| `PUT /v1/partner/applications/{id}/method` | `{"method": DIGILOCKER \| MANUAL}` | `200` (a different choice resets the identity steps) | `409 PREREQUISITE_INCOMPLETE`, `422` |
| `POST /v1/partner/applications/{id}/declaration` | `{"version","accepted":true}` | `200` | `409 DECLARATION_VERSION_OUTDATED`, `409 PREREQUISITE_INCOMPLETE` |
| `POST /v1/partner/applications/{id}/submit` | — (+ `Idempotency-Key`) | `200` status `SUBMITTED`; a repeat with the same key replays it (`Idempotent-Replayed: true`) | `400 IDEMPOTENCY_KEY_REQUIRED`, `409 PREREQUISITE_INCOMPLETE` (+`missing_steps`), `409 IDEMPOTENCY_IN_PROGRESS`, `422 IDEMPOTENCY_KEY_REUSED` |

Any write on a submitted or closed application: `409 APPLICATION_NOT_EDITABLE`. Snapshot fields:
`application_id`, `status`, `version`, `method`, `next_step` (server's answer to "where do I continue",
null when nothing is editable), `expires_at`, `submitted_at`, `steps[]` (`step_key`, `status`,
`attempt_count`, `max_attempts`, `last_error_code`, `summary` with masked values, `reopen_reason_code`,
`reviewer_message`), `available_actions`. Idle applications expire after 30 days (hourly job).

## Operations

| Path | Auth | Note |
|---|---|---|
| `GET /actuator/health`, `/actuator/health/{liveness,readiness}`, `/actuator/info` | public | management port 8081 in the `prod` profile |
