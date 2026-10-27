# ADR-0002: Technology stack and versions

- **Status:** accepted
- **Date:** 2026-10-27
- **Deciders:** project owner

## Context

The ELOKA tech stack reference (26 Oct 2026) fixes the stack. Some combinations need care.

## Decision

| Layer | Choice | Note |
|---|---|---|
| Backend | Java 25, Spring Boot 4.1.x, Spring Modulith 2.1.x, Spring Security | Same versions as eloka, proven together |
| Database | MySQL 8.4 + Flyway | `flyway-mysql` module; `ddl-auto=validate` |
| Frontend | Next.js 15 **Pages Router**, React 18.3, TypeScript, Chakra UI 2.8 | Next 15's App Router needs React 19, so React 18 means Pages Router |
| Node | 22 LTS | Node 20 reached end of life in April 2026 |
| Tests | JUnit 5, Testcontainers (MySQL 8.4), Jest 29, k6 | |
| Delivery | Docker, Docker Compose, Caddy 2.10, GitHub Actions, CodeQL, gitleaks, Trivy, Dependabot | Actions pinned by commit SHA |

## Consequences

- Upgrading to React 19 / App Router or Chakra 3 is a planned migration, not a Dependabot bump (majors are ignored in `dependabot.yml`).
- On the 8 GB development Mac heavy jobs run one at a time (Gradle `--no-daemon --max-workers=1`, Jest `--maxWorkers=2`).
