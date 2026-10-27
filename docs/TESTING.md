# Testing

| Level | Where | Run |
|---|---|---|
| Server unit and integration (JUnit 5, MySQL 8.4 via Testcontainers) | `server/src/test` | `make server-test` (Docker must run) |
| Client unit and component (Jest, Testing Library) | `client/__tests__` | `make client-check` |
| Load (k6) | `server/load/partner-journey.js` | `make load-test` (server running locally) |

## What the server tests prove

- **Rules:** every (status, event) pair of the status machine; prerequisites and invalidation of
  the steps; the CIBIL rule at 299/300/699/700/900/901/no score.
- **Journeys:** step by step and DigiLocker, end to end over HTTP, through submission and staff
  approval.
- **Negative paths:** wrong, expired, locked and reused OTPs; parallel OTP sends (one SMS);
  refresh-token reuse; another applicant's application or file (404); skipped steps (409); a
  missing or stale `If-Match`; a double submit (one submission); a score below 700 (closed, PAN
  blocked); bureau failures and no history (never a pass, blocked after 3); PAN mismatches
  (blocked after 3); PNG photos, tiny photos, renamed files, PDFs as photos, EICAR in a PDF, data
  appended to a JPEG, path names in file names; replayed DigiLocker returns; uploads to steps
  frozen during a correction; reviewer approving their own recommendation; reviewers revealing values.
- **Infrastructure:** Flyway from empty with `ddl-auto=validate`; module boundaries; the audit
  chain re-verified from stored rows; no personal data or tokens in the logs.

## Load targets (proposals)

Until real traffic is known: under 20 concurrent applicants on one small host, p95 below 300 ms for
reading the application and below 500 ms for OTP verification, under 1 % errors. To confirm with
the expected volume before production.

## Merge and deploy gates

- **Block merging:** CI (lint, type check, Jest, client build, server build with all tests),
  Security (gitleaks, dependency review), CodeQL.
- **Block deploying:** Trivy on both images (Security and Deploy workflows), staging `/api/health`
  after deploy, then a person's approval for production.
