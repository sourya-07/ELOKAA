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
| `POST /v1/partner/reauth` | applicant | — | `202 {"expires_in","otp_length"}`: a fresh code to the applicant's own mobile (step-up before withdrawing) | `429 OTP_LIMIT_REACHED` (+`retry_after` s, same limits as sign-in), `503 PROVIDER_UNAVAILABLE` |

Rules: answers are the same for new and known mobiles; the first verified OTP creates the applicant.
Limits (defaults, `eloka.identity.*`): OTP valid 5 min, 5 wrong tries, 30 s between sends, 5 per hour and
10 per day per mobile, 10 per hour per IP. Email codes use the same limits per address and per application
(and per IP), valid 10 min; only a keyed hash of the code is stored. Sessions: access token 30 min, idle 2 h, absolute 12 h.

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
| `POST /v1/partner/applications/{id}/email-verifications` | `{"email"}` (no `If-Match`: changes nothing yet) | `202 {"challenge_ref","expires_in","resend_after","otp_length","email_masked"}`: a code by email, valid 10 min | `422 VALIDATION_FAILED` (`invalid_params.email`), `409 EMAIL_ALREADY_USED` (another open, approved or ineligible application has it), `409 STEP_NOT_EDITABLE` (frozen during a correction), `429 OTP_LIMIT_REACHED` (+`retry_after`), `409 PROVIDER_NOT_CONFIGURED`, `503 PROVIDER_UNAVAILABLE` |
| `POST /v1/partner/applications/{id}/email-verifications/verify` | `{"challenge_ref","otp"}` (+ `Idempotency-Key`) | `200`, completes `EMAIL_VERIFICATION` (summary `email_masked`); a different address than before makes the declaration stale | `410 OTP_EXPIRED` (also used or unknown), `422 OTP_INVALID` (+`attempts_left`), `429 OTP_LOCKED`, `409 EMAIL_ALREADY_USED` |
| `POST /v1/partner/applications/{id}/consents` | `{"purpose": DATA_PROCESSING \| DIGILOCKER \| CREDIT_CHECK, "version"}` | `200` (data-use consent completes `CONSENT`) | `409 CONSENT_VERSION_OUTDATED` (+`current_version`), `409 PREREQUISITE_INCOMPLETE` |
| `POST /v1/partner/applications/{id}/declaration` | `{"version","accepted":true}` | `200` | `409 DECLARATION_VERSION_OUTDATED`, `409 PREREQUISITE_INCOMPLETE` |
| `POST /v1/partner/applications/{id}/submit` | — (+ `Idempotency-Key`) | `200` status `SUBMITTED`; a repeat with the same key replays it (`Idempotent-Replayed: true`) | `400 IDEMPOTENCY_KEY_REQUIRED`, `409 PREREQUISITE_INCOMPLETE` (+`missing_steps`), `409 NOT_ELIGIBLE`, `409 DOCUMENTS_MISSING` (+`missing_documents`, the file rules are checked again), `409 IDEMPOTENCY_IN_PROGRESS`, `422 IDEMPOTENCY_KEY_REUSED` |

| `POST /v1/partner/applications/{id}/withdraw` | `{"otp","reason_code"?}` (+ `Idempotency-Key`; code from `POST /v1/partner/reauth`; `reason_code` one of `CHANGED_MIND`, `TAKES_TOO_LONG`, `WILL_APPLY_LATER`, `OTHER`, anything else is stored as `NONE`) | `200` status `WITHDRAWN` (from `IN_PROGRESS`, `SUBMITTED`, `CORRECTION_REQUIRED`); a repeat with the same key replays it without checking the used code again | `412`, `404` and `409 INVALID_STATE_TRANSITION` are checked first, so they do not use up the code; then `410 OTP_EXPIRED` (no live code), `422 OTP_INVALID` (+`attempts_left`), `429 OTP_LOCKED`; `400 IDEMPOTENCY_KEY_REQUIRED` |

Any write on a submitted or closed application: `409 APPLICATION_NOT_EDITABLE`. Snapshot fields:
`application_id`, `status`, `version`, `method`, `next_step` (server's answer to "where do I continue",
null when nothing is editable), `expires_at`, `submitted_at`, `steps[]` (`step_key`, `status`,
`attempt_count`, `max_attempts`, `last_error_code`, `summary` with masked values, `reopen_reason_code`,
`reviewer_message`), `available_actions`, `requirements[]` (the file checklist: `group`, `step_key`, `rule`
`ALL`/`ONE_OF`, `options` (lists of document kinds), `satisfied`), `submit_blockers[]` (what still stops
submission: `step_key` + `code` such as `STEP_NOT_DONE`, `STEP_BLOCKED`, a step error code, or
`DOCUMENTS_MISSING` with the checklist `group`; empty when `SUBMIT` is available). Idle applications expire
after 30 days (hourly job). Timeline `step_key` is plain text and may name a step a later journey version retired.

## Identity, credit check and documents (`onboarding` with `verification`, `eligibility`, `documents`)

All need an applicant session; writes need `If-Match` unless noted. Every step is completed by the server after
its check; a failed check sets the step to `FAILED` with `last_error_code` and uses one of 3 attempts (then
`BLOCKED`, `423 STEP_BLOCKED`). Technical failures (`503 PROVIDER_UNAVAILABLE`) change nothing.

| Method + path | Body | Success | Errors / outcomes |
|---|---|---|---|
| `POST …/{id}/digilocker-sessions` | — | `200 {"url"}`; ends any earlier unfinished trip; `AADHAAR` → `PENDING_EXTERNAL` (a done step stays done until DigiLocker answers) | `409 CONSENT_REQUIRED` (`DIGILOCKER` consent), `409 STEP_NOT_EDITABLE`, `429 DIGILOCKER_LIMIT_REACHED` (10 per hour) |
| `POST …/{id}/digilocker-sessions/complete` | `{"state"}` (no `If-Match`) | `200` done (the home address completes too when DigiLocker gave a whole one), `202` still waiting; a failed check sets `last_error_code` `DIGILOCKER_NO_AADHAAR_DATA` / `DIGILOCKER_INVALID_REFERENCE` (uses an attempt), `DIGILOCKER_UNDER_AGE` (blocks at once), or `DIGILOCKER_UNAVAILABLE` (an outage: answered `202` like "still waiting", nothing used up, the same trip can be checked again; a done step stays done) | `422` unknown state, `409 DIGILOCKER_ALREADY_USED`, `410 DIGILOCKER_EXPIRED` (15 min) |
| `POST …/{id}/pan-verifications` | `{"pan"}` (+ `Idempotency-Key`); checked against the DigiLocker name and date of birth | `200`, `PAN` done | step fails with `PAN_NAME_MISMATCH`, `PAN_DOB_MISMATCH`, `PAN_INVALID_PAN`, `PAN_ALREADY_USED`; `422` bad PAN / not a personal PAN; `409 AADHAAR_NOT_VERIFIED` |
| `POST …/{id}/credit-assessments` | — (+ `Idempotency-Key`) | `200`: eligible → `CREDIT_CHECK` done (+ DigiLocker address); score below 700 → status `INELIGIBLE` | `409 CONSENT_REQUIRED` (`CREDIT_CHECK` consent); step fails with `BUREAU_UNAVAILABLE`, `NO_CREDIT_HISTORY`, `INVALID_SCORE`. The score is never returned |
| `PUT …/{id}/home-address` | `{"line1","line2","city","district","state","pincode"}` | `200`, `HOME_ADDRESS` done | `422` with `invalid_params`; `409 ADDRESS_FROM_DIGILOCKER` (DigiLocker gave a whole address; only a reviewer's correction reopens it) |
| `POST …/{id}/documents` | multipart `kind`, `file` (no `If-Match`, parallel uploads allowed); `LIVE_PHOTO` also needs `latitude`, `longitude`, `accuracy_m` and `captured_at` (ISO time) from the browser's location service | `201 {"document_id","kind","status","file_name","content_type","size","uploaded_at"}`, plus for the live photo `location: {"accuracy_m","flags"}` (never the coordinates; flags `LOW_ACCURACY` above 100 m, `OUTSIDE_INDIA`) | `422` wrong type/size/too small/malware; live photo: `422 LOCATION_REQUIRED`, `LOCATION_INVALID` (out of range, 0,0, not a number), `LOCATION_TOO_INACCURATE` (above 1000 m, +`accuracy_m`), `LOCATION_STALE` (taken more than 5 min from now); `409 PREREQUISITE_INCOMPLETE`, `409 TOO_MANY_DOCUMENTS` (5 computer certificates), `413 FILE_TOO_LARGE` |
| `GET …/{id}/documents` | — | `200` active files | |
| `GET …/{id}/documents/{documentId}/content` | — | the file, `Cache-Control: no-store` | `404` (also for another applicant's file, and for a file that was replaced or removed) |
| `DELETE …/{id}/documents/{documentId}` | — | `204` | `409` for Aadhaar images |
| `POST …/{id}/photos/submit` | — | `200`, `PHOTOS` done | `409 DOCUMENTS_MISSING` (+`missing_documents`) |
| `POST …/{id}/education/submit` | — | `200`, `EDUCATION` done | `409 DOCUMENTS_MISSING` |
| `POST …/{id}/alternate-id-verifications` | `{"type": VOTER_ID \| DRIVING_LICENCE, "number"}` (+ `Idempotency-Key`) | `200`, `ALTERNATE_ID` done (summary `type`, `number_masked`), or failed with `VOTER_ID_NOT_FOUND`, `*_NAME_MISMATCH`, `*_DOB_MISMATCH`, `DRIVING_LICENCE_NOT_ACTIVE`, `ID_ALREADY_USED` (an attempt each), `ALTERNATE_ID_UNAVAILABLE` (an outage, no attempt) | `422` wrong number format or type, `409 ID_TYPE_EXHAUSTED` (3 tries of this type used: try the other), `423 STEP_BLOCKED`, `409 AADHAAR_NOT_VERIFIED` |
| `POST …/{id}/id-document/submit` | — | `200`, `ID_DOCUMENT` done | `409 DOCUMENTS_MISSING` (+`missing_documents`): both sides of a verified document are needed |

Document kinds: `LIVE_PHOTO`, `PASSPORT_PHOTO` (JPG only, ≤ 5 MB, ≥ 300 px a side); `GRADUATION_CERTIFICATE`,
`COMPUTER_CERTIFICATE`, `VOTER_ID_FRONT`, `VOTER_ID_BACK`, `DRIVING_LICENCE_FRONT`, `DRIVING_LICENCE_BACK`
(JPG or PDF, ≤ 10 MB). Type is read from the bytes; pictures are re-encoded (metadata and trailing data removed);
PDFs are malware-scanned. Every upload waits for staff review (`MANUAL_REVIEW_REQUIRED`). Uploading or removing
a file of a finished step sets it back to `IN_PROGRESS`.

## Staff review (`/v1/admin`, staff session)

Roles: `REVIEWER` (claim, decide evidence, corrections, recommend, hold), `APPROVER` (approve, reject, hold,
reveal hidden values). Roles are read from the account on every request. Applicant sessions get `403` here.

| Method + path | Body | Notes |
|---|---|---|
| `POST /v1/admin/auth/google` | `{"id_token"}` | only verified accounts of `eloka.staff.google-domain` with a staff account; others `403 NOT_STAFF` |
| `POST /v1/admin/auth/otp`, `/auth/otp/verify` | `{"mobile"}`, `{"mobile","otp"}` | local/test only (`404` elsewhere); unknown mobiles get no SMS and can never pass |
| `POST /v1/admin/auth/refresh`, `DELETE /v1/admin/auth/session`, `GET /v1/admin/me` | | `me` returns roles and masked email |
| `GET /v1/admin/applications?status=&page=&size=` | | queue (submitted, under review, on hold), oldest first |
| `GET /v1/admin/applications/{id}` | | case view: snapshot, evidence (masked), address (state, pincode, source, status), ID numbers (masked), eligibility outcome, review history, `approval_blockers`, `photo_location` (latitude, longitude, `accuracy_m`, `captured_at`, `flags` of the live photo) |
| `POST …/{id}/claim`, `…/release` | | reviewer; release by the claimer only |
| `POST …/{id}/documents/{documentId}/decision`, `…/verifications/{evidenceId}/decision`, `…/address/decision` | `{"verified", "reason_code"}` | claimer; a rejection needs a reason |
| `GET …/{id}/documents/{documentId}/content` | | every view is audited |
| `POST …/{id}/corrections` | `{"steps":[AADHAAR\|PAN\|HOME_ADDRESS\|PHOTOS\|EDUCATION\|ID_DOCUMENT], "reason_code","message","due_in_days"}` | status `CORRECTION_REQUIRED`; the declaration is always asked again; other steps stay frozen |
| `POST …/{id}/recommendation` | `{"decision": APPROVE\|REJECT, "note"}` | claimer; APPROVE only when nothing blocks |
| `POST …/{id}/approve` | `{"note"}` | approver who did not recommend (`403 SAME_PERSON`), needs an APPROVE recommendation after the last correction and no blockers |
| `POST …/{id}/reject` | `{"reason_code","note"}` | approver |
| `POST …/{id}/holds`, `DELETE …/{id}/holds` | `{"reason_code"}` | |
| `POST …/{id}/pii-reveal` | `{"field": PAN\|ADDRESS, "reason"}` | approver; audited with the reason, never the value |
| `GET …/{id}/audit` | | the application's audit trail |

## Operations

| Path | Auth | Note |
|---|---|---|
| `GET /actuator/health`, `/actuator/health/{liveness,readiness}`, `/actuator/info` | public | management port 8081 in the `prod` profile |
