# CodeRabbit review log

CodeRabbit CLI (`coderabbit review --agent --uncommitted`) runs once per cohesive task, after lint and tests are green. The included allowance is 3 reviews per rolling hour (`coderabbit usage`). Critical and major findings are fixed before the commit.

| Date | Task | Severity | File | Finding | Outcome |
|---|---|---|---|---|---|
| 2026-10-27 | P2 bootstrap | major | `client/Dockerfile` | runtime `COPY public` fails if `client/public/` is missing | no change: `client/public/.gitkeep` is part of the same commit |
| 2026-10-27 | P3 server foundation | major | `audit/internal/DatabaseAuditLog.java` | hash used nanosecond time but `DATETIME(6)` stores microseconds, so the chain could not be re-verified | fixed: time truncated to microseconds; test re-verifies the chain from stored rows. **The eloka repo has the same bug.** |
| 2026-10-27 | P3 server foundation | minor | `audit/internal/DatabaseAuditLog.java` | `|`-joined canonical form is ambiguous | fixed: length-prefixed fields |
| 2026-10-27 | P3 server foundation | minor | `shared/crypto/CryptoConfig.java` | published test keys not refused; string compare misses padding/whitespace variants | fixed: decoded-byte compare, test keys listed, test added |
| 2026-10-27 | P3 server foundation | minor | `shared/crypto/Mobiles.java` | leading trunk `0` rejected | fixed: `0XXXXXXXXXX` accepted |
| 2026-10-27 | P3 server foundation | minor | `server/compose.yaml` | local MySQL published on all interfaces | fixed: bound to 127.0.0.1 |
| 2026-10-27 | P4 applicant sign-in | major | `identity/internal/OtpService.java` | parallel sends for one mobile all passed the limits (SMS cost abuse); a transaction stayed open during the provider call | fixed: per-mobile and per-IP guard rows locked while limits are checked and the challenge reserved; provider called outside any transaction; attempts reserved before verification; single-use enforced on consume. Test: 6 parallel sends → one 202 |
| 2026-10-27 | P4 applicant sign-in | major | `identity/internal/ApplicantSessionController.java` | per-IP limit would see the proxy's address | no server change (prod trusts `X-Forwarded-For` from private proxies); requirement added to ADR-0003 for the client proxy (P10) |
| 2026-10-27 | P4 applicant sign-in | minor | `integration/internal/EpsClient.java` | a non-numeric `status` read as 0 (success) | fixed: only an integral `status` is accepted, anything else is `PROVIDER_UNAVAILABLE` |
| 2026-10-27 | P5 application core | — | — | review deferred: hourly allowance used up | to review with `coderabbit review --committed --base-commit 1459ab6` together with P6 |
| 2026-10-27 | P5–P8 server (with the deferred P5 review) | major | `onboarding/internal/EligibilityStep.java` | the credit decision was not in the audit log | fixed: `CREDIT_ASSESSED` (outcome, reused, failure code; never the score), plus `PAN_CHECKED` and `DIGILOCKER_RESULT`; test checks the details hold no score |
| 2026-10-27 | P5–P8 server | major | `eligibility/internal/EligibilityService.java` | an out-of-range score would break the database check instead of being recorded | fixed: invalid scores are not stored; test with fake score 950 |
| 2026-10-27 | P5–P8 server | major | `onboarding/internal/DocumentSteps.java` | files could be attached to a step frozen during a correction | fixed: `409 STEP_NOT_EDITABLE` for locked steps |
| 2026-10-27 | P5–P8 server | minor | `V9__credit_assessment.sql` | `ELIGIBLE`/`INELIGIBLE` rows could have no score | fixed: score required by the check constraint |
| 2026-10-27 | P5–P8 server | minor | `onboarding/internal/IdentitySteps.java` | DigiLocker address could try to complete a locked or done step | fixed: skipped when already satisfied |
| 2026-10-27 | P5–P8 server | minor | `verification/internal/VerificationService.java` | DigiLocker date of birth skipped the 18+ rule | fixed |
| 2026-10-27 | P5–P8 server | minor | `documents/internal/DocumentService.java` | a re-encoded photo could exceed the size limit | fixed: size checked again after re-encoding |
| 2026-10-27 | P9 staff review | major | `onboarding/internal/ReviewService.java` | the free-text reason for revealing a hidden value went into the audit log (may name people) | fixed: kept in the staff review history only; the audit records the field; test checks |
| 2026-10-27 | P9 staff review | major | `onboarding/internal/ReviewService.java` | reason codes and the step list could overflow their columns | fixed: reason codes validated (`[A-Z][A-Z0-9_]{1,39}`), target cut to 40 characters |
| 2026-10-27 | P9 staff review | minor | `identity/StaffView.java` | `/me` used camelCase names | fixed: snake_case |
| 2026-10-27 | P9 staff review | minor | `integration/internal/GoogleIdTokenVerifier.java` | a failed fetch of Google's keys looked like a bad token; no timeouts | fixed: `503 PROVIDER_UNAVAILABLE`, 5 s / 10 s timeouts |
| 2026-10-27 | P10–P12 client | major | `client/src/pages/api/v1/[...path].ts` | parallel calls with an expired token both refreshed; the second use of the old refresh token makes the server end the session | fixed: one refresh per refresh token shared by concurrent calls (`singleFlight`), test added |
| 2026-10-27 | P10–P12 client | major | `client/src/lib/steps.ts` | DigiLocker applicants without a full address could not reach the address page | fixed: `HOME_ADDRESS` always routes to `/apply/address`; test added |
| 2026-10-27 | P10–P12 client | major | `client/src/features/apply/ApplicationContext.tsx` | approved applicants looped on loading | fixed: an `ALREADY_APPROVED` answer loads the current (approved) application |
| 2026-10-27 | P10–P12 client | major | `client/src/pages/apply/digilocker.tsx` | a stale "waiting" value dropped the state, so "Check again" did nothing | fixed: the completion result decides |
| 2026-10-27 | P10–P12 client | minor | `client/src/lib/api.ts` | a retried action got a new Idempotency-Key | fixed: one key per action and application version, reused on retry |
| 2026-10-27 | P10–P12 client | minor | `client/src/components/CameraCapture.tsx` | live-photo upload failures were not shown | fixed |
| 2026-10-27 | P10–P12 client | minor | `client/src/features/apply/ApplicationContext.tsx` | a failed load showed a spinner forever | fixed: message and "Try again" |
| 2026-10-27 | P13–P14 delivery | major | `.github/workflows/deploy.yml` | a job-level `if` cannot read environment variables, so deploys would always be skipped | fixed: the check runs as a step inside the environment |
| 2026-10-27 | P13–P14 delivery | major | `deploy/docker-compose.yml`, `client/Dockerfile` | the staff Google client id was compiled into the client image, so published images hid Google sign-in | fixed: read per request from `ELOKA_STAFF_GOOGLE_CLIENT_ID`; one image for every environment |
| 2026-10-27 | P13–P14 delivery | major | `identity/internal/StaffService.java` | the public staff save ran without a transaction (self-call) | fixed: `@Transactional` |
| 2026-10-27 | P13–P14 delivery | major | `deploy/remote-deploy.sh` | the backup ran after switching to the new release's files | fixed: backup first |
| 2026-10-27 | P13–P14 delivery | major | `deploy/remote-deploy.sh` | does the public health route exist? | no change: `client/src/pages/api/health.ts`, reached through Caddy |
| 2026-10-27 | P13–P14 delivery | minor | `deploy/restore.sh` | a relative backup path was resolved after changing directory | fixed |
| 2026-10-27 | P13–P14 delivery | minor | `docs/deploy/RUNBOOK.md`, `server/load/partner-journey.js` | unset shell variable in the health check; per-IP OTP limit in the load test | fixed / documented |
| 2026-10-27 | Withdraw + step-up (8237326, reviewed after commit: hourly limit) | major | `client/src/features/apply/WithdrawPanel.tsx` | the code box assumed 6 digits instead of the `otp_length` the server sends | fixed |
| 2026-10-27 | Withdraw + step-up | major | `client/src/pages/apply/status.tsx` | applicants still filling in the form had no link to the status page, so they could not find "withdraw" | fixed: "My application" link in the header of every journey step |
| 2026-10-27 | Withdraw + step-up | minor | `onboarding/internal/ApplicationController.java` | a stale version or someone else's id used up the single-use code before the 412/404 | fixed: checked before the code, and again under the row lock |
| 2026-10-27 | Withdraw + step-up | minor | `client/src/features/apply/WithdrawPanel.tsx`, `ApplicationController.java` | a retry after a lost answer checked the used code again (`410`) | fixed: `Idempotency-Key` required; a repeat replays the result |
| 2026-10-27 | Withdraw + step-up | minor | `onboarding/internal/ApplicationService.java` | any code-shaped reason was stored, not only the defined ones | fixed: fixed list, everything else `NONE` |
| 2026-10-28 | Per-environment settings files | major | `docs/deploy/RUNBOOK.md` | the manual deploy ran the backup without picking the environment's settings file | fixed: `ELOKA_ENV_FILE=.env.$E deploy/backup.sh` |
| 2026-10-28 | Journey motion | minor | `client/src/styles/theme.ts` | the press motion was in the global Button theme, so staff buttons moved too | fixed: press and banner fade set on the journey frame (`journeyFeedback` in ApplyShell); staff stays still |
