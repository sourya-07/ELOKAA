# ADR-0001: One repo with client and server, built by porting proven eloka parts

- **Status:** accepted
- **Date:** 2026-10-27
- **Deciders:** project owner

## Context

ELOKA partner onboarding is a new, person-centred journey (mobile OTP, DigiLocker or step by step, Aadhaar, PAN, CIBIL, photos, certificates, voter ID or driving licence). The Local Admin application in `sourya-07/eloka` already has a tested OTP login, step engine, encrypted documents, audit log, staff review and deploy stack, but it is tied to the EPS wire format, tenants and a business journey.

## Decision

- This repository (`sourya-07/ELOKAA`) holds only partner onboarding: `client/` (Next.js), `server/` (Spring Boot), `deploy/`, `docs/`, `eloka_design/` (Stitch designs).
- `client/` and `server/` are independent: own dependencies, build, tests and image. They talk only over HTTP.
- Code that already works in eloka is **ported** (copied, then trimmed to this domain) instead of rewritten: shared crypto/errors/logging/security, audit, OTP and sessions, the step-engine pattern, documents, provider ports, CI and the deploy stack. Every ported file is read and adapted; nothing is copied blind.
- Not ported: the EPS gateway, wallet, network, history, pricing, tenant logic, `apiHelper.js` (logs request bodies), analytics scripts, tokens in `sessionStorage`.

## Options considered

| Option | Pros | Cons |
|---|---|---|
| Fresh repo, port proven parts (chosen) | Clean domain, reuses tested code | Two codebases to keep in step |
| Fresh repo, write everything | No legacy | Repeats tested work, slower |
| Add to eloka | Nothing to copy | Mixes two journeys and the EPS wire format |

## Consequences

- Fixes found in one repo must be checked against the other.
- The repo is **public**: no real personal data, credentials or internal PDFs are ever committed (gitleaks in CI and pre-commit).
