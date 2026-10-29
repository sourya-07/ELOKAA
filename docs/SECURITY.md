# Security and privacy

What the system does today, and what still needs a decision. Legal and compliance points are
questions for counsel, not claims of compliance.

## Built in

| Area | How |
|---|---|
| Sessions | opaque 256-bit tokens, only SHA-256 stored; 30-min access, 2-h idle, 12-h absolute; refresh rotation, reuse of an old refresh token ends the session; kept in `HttpOnly; Secure; SameSite=Strict` cookies by the client's proxy (ADR-0003) |
| CSRF | SameSite=Strict, `X-Requested-With: eloka` and same-origin `Origin` required on writes at the proxy |
| OTP | mobile: code never seen or stored (provider owns it); email: our own random code, only a keyed hash stored, valid 10 min, same limits per address, application and IP, checked in its own transaction so wrong guesses always count; 5-min expiry, 5 tries, single use, 60-s gap (enforced on the server; the page only shows the wait), 5/hour and 10/day per mobile, 10/hour per IP; limits serialised per mobile and IP; unknown staff mobiles get no SMS |
| Authorization | default deny; `/v1/partner/**` applicant sessions only, `/v1/admin/**` staff only; staff roles read on every request; another applicant's data answers 404 |
| Server-side rules | step order, verification, the CIBIL rule (700, a constant), submission and approval checked on the server; approval needs every item verified and a second person |
| Personal data at rest | PAN, mobile, names, dates of birth, addresses, ID numbers encrypted (AES-256-GCM); blind indexes (HMAC) for uniqueness; masked copies for display; the full Aadhaar number is never a stored field, but the full Aadhaar card images required from journey v2 contain it (ADR-0008: encrypted, claimed-reviewer or approver viewing only, every view audited, no OCR; production waits for Q-20); never the credit report |
| Live-photo location | required with the live photo, never invented; range, accuracy (1000 m) and capture time (required, ±5 min) checked on the server; coordinates encrypted (AES-256-GCM) in `photo_location`, apart from the image (whose EXIF is stripped); the applicant sees their own coordinates read-only, labelled "reported by your browser … not a checked address" (decrypted per request for the owner, never stored in a step summary or logged); staff see the coordinates; no reverse geocoding and no IP location (it would send the coordinates to a third party; docs/OPEN_QUESTIONS.md Q-31); a browser location can be faked, so it is evidence, not proof |
| Uploads | type from the bytes; per-kind formats and sizes; JPEGs decoded and re-encoded (metadata and appended data removed, 40-MP cap); PDFs malware-scanned; encrypted on a private volume (a refused or rolled-back upload removes its file, so nothing is left on disk without a record); served only to the owner or audited staff, `no-store` |
| Logs | no request bodies; `PiiMasker` on every line (PAN, voter ID numbers, mobiles, Aadhaar-like numbers, email addresses, tokens, OTP fields); a test runs a whole application and checks the logs |
| Audit | every sign-in, consent, check, credit decision, review action and reveal in a hash-chained table, in the same transaction as the change; free text kept out of it |
| Headers | CSP with a per-request nonce (no inline scripts), HSTS, `nosniff`, `frame-ancestors 'none'`, camera and location allowed for this site only (`Permissions-Policy`), microphone off |
| Providers | allow-listed EPS hosts over HTTPS only; fakes and published keys refuse to start in staging and production |
| Supply chain | Actions pinned by SHA; gitleaks over the full history; Trivy on both images; CodeQL; Dependabot |

## Needs a decision or confirmation

- Credit data: legal route under the Credit Information Companies (Regulation) Act 2005 and consent wording (Q-1).
- Aadhaar: the masked-image path for step-by-step applicants (Q-9) and DigiLocker via EPS.
- DPDP Act: consent texts (versions `2026-10-27` are placeholders), retention and deletion periods (Q-5).
- Liveness / face match (Q-3): live photos are reviewed by a person only.
- Rate limits are per server process; review when running more than one instance.
- A penetration test before production.
