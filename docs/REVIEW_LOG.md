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
