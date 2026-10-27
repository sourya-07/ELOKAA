# ELOKA partner onboarding

The web journey through which a prospective ELOKA partner verifies their mobile number, proves their identity (with DigiLocker or step by step), passes a CIBIL eligibility check (score 700 or more), uploads photos and documents, and submits the application for review by Eko staff.

> This repository is **public**. Never commit real names, mobile numbers, PAN or Aadhaar numbers, credentials or internal documents.

## Where things are

| Path | What |
|---|---|
| [`docs/PARTNER_JOURNEY.md`](docs/PARTNER_JOURNEY.md) | pages, steps, states and the rules the server enforces |
| [`docs/adr/`](docs/adr/) | architecture decisions |
| [`docs/OPEN_QUESTIONS.md`](docs/OPEN_QUESTIONS.md) | what is still undecided and the default we build on |
| [`eloka_design/`](eloka_design/README.md) | Google Stitch designs (png + html) |
| `client/` | Next.js web app (skeleton) |
| `server/` | Spring Boot backend (skeleton) |
| `deploy/` | Docker Compose + Caddy production stack (planned) |

## Stack

Java 25, Spring Boot 4.1, Spring Modulith, Spring Security, MySQL 8.4, Flyway · Next.js 15 (Pages Router), React 18, TypeScript, Chakra UI 2 · JUnit, Testcontainers, Jest, k6 · Docker, Caddy, GitHub Actions, CodeQL, gitleaks, Trivy, Dependabot. Details and version notes: [ADR-0002](docs/adr/0002-technology-stack.md).

## Status

- 2026-10-27: decisions, journey and ADRs written. No application code yet.
- 2026-10-27: repo bootstrap: client and server skeletons that build and test, CI, security scans (gitleaks, dependency review, Trivy), CodeQL, Dependabot, Dockerfiles, Makefile.
- 2026-10-27: server foundation (P3): shared errors, security (default deny), crypto, masked logging, safety guard, hash-chained audit log, Flyway V1–V2, Testcontainers MySQL test.
