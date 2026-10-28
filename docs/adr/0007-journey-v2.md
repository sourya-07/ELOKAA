# ADR-0007: Journey v2: DigiLocker identity, email OTP, verified alternate ID, five working pages

- **Status:** accepted; slices 0–9 built on local fakes (2026-10-28); real providers waiting (see "Build order")
- **Date:** 2026-10-28
- **Deciders:** project owner

## Context

The owner asked for a reshaped applicant journey:

- the credit rule (CIBIL 700 or more) stated before anything else;
- mobile **and** email OTP;
- identity through DigiLocker (Aadhaar), PAN, and a voter ID or driving licence that is actually verified;
- the credit check right after PAN;
- a live photo with latitude and longitude, plus a passport-size photo;
- a full document set (Aadhaar front and back, PAN, voter ID or driving licence, class 10 and 12 marksheets, graduation or diploma);
- a review with corrections before submission.

The P0–P14 build has one page per step (10 pages), counts progress in screens, and verifies voter ID and driving licence only by staff eye.

## Decision

- **Steps (server-enforced order):** `MOBILE_VERIFICATION → EMAIL_VERIFICATION → CONSENT → AADHAAR (DigiLocker) → HOME_ADDRESS → PAN → CREDIT_CHECK → ALTERNATE_ID → PHOTOS → KYC_DOCUMENTS, EDUCATION → DECLARATION`.
- **DigiLocker is required for Aadhaar.** The step-by-step (masked images) path is retired.
- **Aadhaar images are full, unmasked images**, by the owner's decision; see ADR-0008 and Q-20 for the controls and the open legal question.
- **CIBIL runs right after PAN**, before the voter ID or driving licence check (ADR-0005 unchanged).
- **Voter ID or driving licence** is verified through the EPS Voter ID and Driving Licence APIs plus EPS Name Match. When both fail, the step blocks and staff follow up; nothing is marked verified from an image alone.
- **The live photo needs real browser coordinates.** There is no way past a denied or failed location; coordinates are never invented.
- **Pages are not steps.** The applicant UI has five working pages (contact, identity task list, photos, documents, review) plus the landing and status pages. Progress is six business milestones computed from the server's step statuses.
- **Redoing a step with the same data does not reset later steps.** Each step stores a blind-index fingerprint of the data it was completed with (`application_step.data_hash`).
- **One file checklist** (`DocumentRequirements`) serves the step submits, the snapshot (`requirements`, `submit_blockers`) and the final submit.
- **Consent texts are versioned per purpose**, so a new data-use text does not void DigiLocker or credit-check consents.

## Options considered

| Option | Pros | Cons |
|---|---|---|
| Keep one page per step, add the new steps | little client work | 13+ screens, repeated instructions, progress still counts screens |
| Task-list pages over the same step engine (chosen) | fewer screens, progress follows real completion, engine reused | larger client rewrite, a migration for the new step rows |
| Keep the manual Aadhaar path as a fallback | works without DigiLocker | not provider-verified; the owner wants DigiLocker |

## Consequences

- EPS has no email OTP: an `EmailSender` port with an SMTP adapter is needed, and production needs an SMTP account and domain records (Q-21).
- EPS documents DigiLocker for Aadhaar only. PAN stays on PAN Lite; voter ID and driving licence use their own EPS APIs unless EPS confirms DigiLocker can return them (Q-22).
- Applications in progress on the old step model cannot be carried over automatically. The migration that changes steps refuses to run while any non-terminal application exists (none exist in a deployed environment yet).
- Production still depends on Q-1 (credit bureau), Q-20, Q-21 and EPS UAT evidence.

## Build order

Vertical slices, each with its tests and docs, so `main` works after every one:

1. Foundation: fingerprints, per-purpose consent versions, file checklist, milestones, landing page.
2. Contact: email OTP.
3. Identity: DigiLocker only.
4. Alternate ID.
5. Photos and location.
6. Documents.
7. Review, status and staff.
8. Motion and design pass.
9. End-to-end tests.
10. Real providers.

Slices 0–9 were done on 2026-10-28 (with each slice's tests, docs and review). Slice 10 waits on Q-1, Q-20,
Q-21 and EPS UAT access (Q-22, Q-23). Not done from the plan: a CI job for the browser tests (it needs
MySQL, the server and the web app in one runner) and updated Stitch screens (below).
