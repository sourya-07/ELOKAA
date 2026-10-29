# ELOKA partner onboarding

The web journey through which a prospective ELOKA partner verifies their mobile number, verifies their email, proves their identity through DigiLocker (Aadhaar), PAN and a voter ID or driving licence, passes a CIBIL eligibility check (score 700 or more), uploads photos and documents, and submits the application for review by Eko staff.

> This repository is **public**. Never commit real names, mobile numbers, PAN or Aadhaar numbers, credentials or internal documents.

## Where things are

| Path | What |
|---|---|
| [`docs/PARTNER_JOURNEY.md`](docs/PARTNER_JOURNEY.md) | pages, steps, states and the rules the server enforces |
| [`docs/adr/`](docs/adr/) | architecture decisions |
| [`docs/OPEN_QUESTIONS.md`](docs/OPEN_QUESTIONS.md) | what is still undecided and the default we build on |
| [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md), [`docs/API.md`](docs/API.md), [`docs/ERROR_CATALOG.md`](docs/ERROR_CATALOG.md), [`docs/SECURITY.md`](docs/SECURITY.md), [`docs/TESTING.md`](docs/TESTING.md), [`docs/LOCAL_DEVELOPMENT.md`](docs/LOCAL_DEVELOPMENT.md) | how it fits together, the endpoints, security, tests, running it locally |
| [`eloka_design/`](eloka_design/README.md) | Google Stitch designs (png + html) |
| `client/` | Next.js web app: applicant journey (`/apply`), staff console (`/staff`), BFF proxy |
| `server/` | Spring Boot backend: sign-in, application, checks, documents, review |
| `deploy/` | Docker Compose + Caddy stack, backup/restore, remote deploy ([runbook](docs/deploy/RUNBOOK.md)) |

## Stack

Java 25, Spring Boot 4.1, Spring Modulith, Spring Security, MySQL 8.4, Flyway · Next.js 15 (Pages Router), React 18, TypeScript, Chakra UI 2 · JUnit, Testcontainers, Jest, k6 · Docker, Caddy, GitHub Actions, CodeQL, gitleaks, Trivy, Dependabot. Details and version notes: [ADR-0002](docs/adr/0002-technology-stack.md).

## Status

- 2026-10-27: decisions, journey and ADRs written. No application code yet.
- 2026-10-27: repo bootstrap: client and server skeletons that build and test, CI, security scans (gitleaks, dependency review, Trivy), CodeQL, Dependabot, Dockerfiles, Makefile.
- 2026-10-27: server foundation (P3): shared errors, security (default deny), crypto, masked logging, safety guard, hash-chained audit log, Flyway V1–V2, Testcontainers MySQL test.
- 2026-10-27: applicant sign-in (P4): mobile OTP through a provider port (local fake, EPS adapter, "not configured" stub), rate limits, sessions with refresh rotation, Flyway V3. API: [`docs/API.md`](docs/API.md).
- 2026-10-27: application core (P5): partner application and its 11 steps, status machine, If-Match/ETag, Idempotency-Key on submit, consent records, declaration, expiry job, Flyway V4–V5.
- 2026-10-27: Security workflow fixed: Tomcat 11.0.25 and Jackson 3.1.7 pinned (server image), postcss 8.5.x forced (client image), gitleaks now scans the full history with a checksum-pinned CLI.
- 2026-10-27: identity, credit check and documents (P6–P8): PAN (EPS PAN Lite adapter), DigiLocker (EPS adapter, single-use state), masked Aadhaar upload, home address, CIBIL check with the 700 rule (fake bureau only), photos, certificates, voter ID / driving licence, encrypted uploads with content checks and malware scan, Flyway V6–V9. Local fakes: [`docs/LOCAL_DEVELOPMENT.md`](docs/LOCAL_DEVELOPMENT.md).
- 2026-10-27: staff review (P9): staff sign-in (Google; OTP locally), roles, queue, case view, evidence decisions, corrections, maker-checker approval, reject, hold, audited reveal of hidden values, Flyway V10.
- 2026-10-27: client (P10–P12): BFF proxy with HttpOnly session cookies, CSRF checks and token refresh; CSP with a per-request nonce; theme from DESIGN.md; all applicant pages (mobile OTP, method, DigiLocker, Aadhaar, PAN, credit check, address, photos with camera, education, voter ID / DL, review, status); staff console (sign-in, queue, case review).
- 2026-10-27: delivery (P13–P14): production compose stack (MySQL, migrate job, server, client, ClamAV, Caddy), backup/restore, tag-driven deploy workflow (scan, publish, staging, approved production), runbook, staff bootstrap command, k6 script, log-leak test, architecture/security/testing docs.
- 2026-10-28: journey v2 (ADR-0007), slices 0–9: existing defects fixed; email OTP; identity through DigiLocker only (the step-by-step path is retired); CIBIL right after PAN; voter ID or driving licence verified through EPS with a name match; live photo with the device location (required); full Aadhaar images and the full document set (ADR-0008); five working pages with six-part progress; review page with blockers and fix links; staff unblock and a Blocked queue; motion and accessibility pass; Playwright end-to-end tests with axe (local fakes only). Flyway V11–V17.
- Not production-ready: the credit bureau (Q-1), the legal basis for full Aadhaar images (Q-20), an SMTP account (Q-21), EPS UAT runs for every adapter (Q-22, Q-23), hosting (Q-4), retention (Q-5), reviewed consent texts and a penetration test are still open ([`docs/OPEN_QUESTIONS.md`](docs/OPEN_QUESTIONS.md)).
