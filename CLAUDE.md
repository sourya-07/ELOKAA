# CLAUDE.md

Guidance for anyone (people and AI agents) changing this repository. Read this first, then the `CLAUDE.md` of the folder you work in ([`client/`](client/CLAUDE.md), [`server/`](server/CLAUDE.md)). Keep these files true: when a structure, command or rule changes, update them in the same commit.

## What this project is

ELOKA partner onboarding: an individual applies to become an ELOKA partner (mobile and email OTP, identity through DigiLocker, CIBIL ≥ 700, photos, certificates, voter ID or driving licence; journey v2, ADR-0007) and Eko staff review the application. Journey and rules: [`docs/PARTNER_JOURNEY.md`](docs/PARTNER_JOURNEY.md). Decisions: [`docs/adr/`](docs/adr/). Unknowns: [`docs/OPEN_QUESTIONS.md`](docs/OPEN_QUESTIONS.md).

Proven parts are ported from the eloka repo (`sourya-07/eloka`, ADR-0001). Read the eloka original before porting a file, then trim it to this domain.

**This repository is public.** Never commit real names, mobile numbers, PAN/Aadhaar numbers, credentials, internal PDFs or photos.

## Repository map

```
ELOKAA/
├── client/        Next.js 15 (Pages Router) + React 18 + TypeScript + Chakra UI 2
├── server/        Java 25 + Spring Boot 4.1 + Spring Modulith + MySQL 8.4 + Flyway
├── deploy/        Docker Compose + Caddy stack, backup/restore, remote deploy (docs/deploy/RUNBOOK.md)
├── docs/          journey, ADRs, open questions
├── eloka_design/  Google Stitch designs (png + html); final/ is what we build from
├── .github/       CI, security scans, CodeQL, Dependabot
└── Makefile       `make help`
```

`client/` and `server/` are independent (own dependencies, build, tests, image) and talk only over HTTP.

## Commands

| Task | Command |
|---|---|
| Client checks (like CI) | `make client-check` |
| Client dev server | `make client-dev` → <http://localhost:3003> |
| Server tests | `make server-test` |
| Server locally | `make server-run` → <http://localhost:8091/actuator/health> |
| Browser end to end (stack running) | `cd client && npm run e2e` (Playwright, Chromium, one worker) |
| Both images | `make images` |
| Load test (server running) | `make load-test` |
| Staging / production / test stack | `docker compose -f deploy/docker-compose.yml --env-file deploy/.env.<staging\|production\|test> up -d --wait` ([`docs/deploy/RUNBOOK.md`](docs/deploy/RUNBOOK.md)) |

The development Mac has 8 GB RAM: run one heavy job at a time (Gradle, Jest, `next build`, Docker builds, k6). The Makefile targets already limit memory. Gradle needs a JDK 17+ to start: `export JAVA_HOME=/opt/homebrew/opt/openjdk`.

## Rules

- **Read before you write.** Follow the existing pattern; match the surrounding style and comment density.
- **The server decides.** Step order, verification results, eligibility and status changes are enforced on the server. The client only displays them.
- **Every external system behind a port** with a local fake and a "not configured" stub (ADR-0004). Fakes never run outside `local`/`test`.
- **Personal data:** never log OTPs, tokens, PAN, Aadhaar, credit data or document bytes. Mask by default in logs and UI.
- **Configuration, not conditionals.** Environments differ only in configuration (Spring profiles, env vars).
- **Small commits**, Conventional Commits (`feat(server): …`), on `main`. Tests and docs are part of the same commit.
- Before committing: the checks for every folder you touched (`make client-check`, `make server-test`). The Security workflow (Trivy image scan, gitleaks) runs on every push, including docs-only ones: check `gh run list` after a push and fix red runs.
- Security version pins live in `server/build.gradle.kts` (`extra[...]`) and `client/package.json` (`overrides`). Remove one only when the managed version is at least as high.
