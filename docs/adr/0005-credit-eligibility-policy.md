# ADR-0005: CIBIL eligibility is a backend rule, checked right after PAN

- **Status:** accepted
- **Date:** 2026-10-27
- **Deciders:** project owner

## Context

A partner is eligible only with a CIBIL score of 700 or more. On the owner's whiteboard the check sits before onboarding ("Lead"); the project brief places it after the documents.

## Decision

- The check runs **right after the PAN is verified**, before photos and certificates. An applicant below 700 never uploads them (DPDP data minimisation) and loses no time.
- It needs a separate, recorded consent for the credit check (purpose `CREDIT_CHECK`).
- Outcomes: `PENDING`, `ELIGIBLE` (score ≥ 700), `INELIGIBLE` (score < 700), `UNABLE_TO_ASSESS` (timeout, invalid or unmatched response, no record). `UNABLE_TO_ASSESS` is retried a limited number of times and then needs staff follow-up. It never counts as eligible.
- 700 is a constant in `EligibilityPolicy`, not a setting, so changing it takes a reviewed code change.
- `INELIGIBLE` closes the application. No override exists.
- Only the score, outcome, provider reference and time are stored, never the credit report.
- Every endpoint that needs eligibility (next steps, submit, approve) asks the same `EligibilityPolicy`; the client's view is display only.

## Consequences

- Production needs a legal route to credit data (Credit Information Companies (Regulation) Act 2005, consent wording) and a provider contract. Until then `CreditBureau` has only the local fake and the "not configured" stub (ADR-0004).
