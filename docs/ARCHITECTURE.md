# Architecture

```mermaid
flowchart LR
  B[Browser] -->|HTTPS| C[Caddy: TLS, one host name]
  C --> W["client (Next.js 15, Pages Router)<br/>pages + /api/v1 proxy (BFF)<br/>session in HttpOnly cookies"]
  W -->|Bearer token, internal network| S
  subgraph S["server (Spring Boot 4.1, Spring Modulith)"]
    ID[identity: applicants, staff, OTP, sessions]
    ON["onboarding: application, steps, status machine,<br/>identity/document/credit steps, staff review"]
    VE[verification: PAN, Aadhaar, DigiLocker]
    EL[eligibility: CIBIL rule, assessments]
    DO[documents: checks, re-encode, scan, encrypted storage]
    IN[integration: provider ports, EPS adapters, fakes, stubs]
    AU[audit: hash-chained events]
    SY[system: migrate, staff bootstrap]
    SH[shared: errors, security, crypto, masked logging, safety guard]
  end
  S --> M[(MySQL 8.4, Flyway)]
  DO --> F[(encrypted file volume)]
  IN --> EPS[EPS: mobile OTP, PAN Lite, DigiLocker]
  IN --> CB[credit bureau: not contracted]
  IN --> AV[ClamAV]
  IN --> G[Google ID tokens: staff]
```

## Modules (server)

Each module's top-level package is its API; `internal` is private. `ModularityTests` fails the
build on a violation or a cycle. Dependencies point one way:
`onboarding → {identity, verification, eligibility, documents, audit}` and
`{verification, eligibility, documents, identity} → integration → shared`.

| Module | Owns | Public API |
|---|---|---|
| `shared` | errors (problem+json), security config, crypto, masked logging, safety guard | open module |
| `audit` | `audit_event` (hash chain) | `AuditLog`, `AuditTrail` |
| `integration` | ports and adapters, chosen by `eloka.integration.*` | `MobileOtpGateway`, `PanVerifier`, `DigiLockerProvider`, `CreditBureau`, `MalwareScanner`, `GoogleIdentityVerifier` |
| `identity` | applicants, OTP challenges, sessions, staff accounts | `StaffDirectory`, `StaffAdmin`, `IdentityProperties` |
| `documents` | stored files and their rules | `DocumentApi`, `DocumentKind` |
| `verification` | PAN and Aadhaar checks, DigiLocker sessions | `VerificationApi`, `EvidenceStatus` |
| `eligibility` | credit assessments | `EligibilityApi`, `EligibilityPolicy` |
| `onboarding` | the application, its steps and review | `PartnerStep`, `ApplicationStatus`, events |
| `system` | run-once commands | — |

## Request path

1. The browser calls `/api/v1/...` on its own origin. The proxy checks the path, the anti-forgery
   header and the Origin, adds the Bearer token from the HttpOnly cookie and forwards the call.
2. The server resolves the token to a `Caller` (applicant or staff, roles read fresh), Spring
   Security allows the route by session kind, and the controller calls a service.
3. Application writes take the application's row lock, check `If-Match`, run the step rules and
   the status machine, write the audit event and the timeline in the same transaction.
4. Provider calls go through ports; a failure never counts as a pass.

The rules the server enforces: [PARTNER_JOURNEY.md](PARTNER_JOURNEY.md). Endpoints: [API.md](API.md).
Decisions: [adr/](adr/).
