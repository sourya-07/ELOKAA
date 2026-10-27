# ADR-0004: Every external check behind a port: real adapter, local fake, or "not configured"

- **Status:** accepted
- **Date:** 2026-10-27
- **Deciders:** project owner

## Context

The journey depends on outside services: SMS OTP, PAN, DigiLocker, voter ID, driving licence, a credit bureau (CIBIL), malware scanning and, later, face match. Eko's own EPS APIs offer mobile OTP, PAN Lite/Advanced, DigiLocker, voter ID, driving licence and name match. EPS has **no** credit bureau, face match or generic Aadhaar OTP API. No credentials exist yet.

## Decision

- Each service is an interface in the `integration` module (`OtpSender`, `PanVerifier`, `DigiLockerProvider`, `CreditBureau`, `VoterIdVerifier`, `DrivingLicenceVerifier`, `MalwareScanner`).
- Each has up to three implementations:
  1. a real adapter (EPS first), enabled by configuration;
  2. a **fake** for the `local` and `test` profiles only. Fakes implement `UnsafeInProduction`;
  3. an **unconfigured** stub for every other case, which answers `409 PROVIDER_NOT_CONFIGURED`. It never pretends to succeed.
- `ProductionSafetyGuard` stops the application at start-up in `staging` and `production` if any fake, development key or fixed OTP is active.
- A technical failure is recorded as a failure (`VERIFICATION_FAILED`, `UNABLE_TO_ASSESS`), never as a pass.

## Consequences

- The whole journey runs locally with fakes, and the same build runs in production once real adapters and keys exist.
- Adding a provider means one adapter class plus configuration; business code does not change.
