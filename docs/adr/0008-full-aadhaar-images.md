# ADR-0008: Full Aadhaar images, with restricted access

- **Status:** accepted for building; **not for production** until Q-20 is answered
- **Date:** 2026-10-28
- **Deciders:** project owner

## Context

Journey v2 (ADR-0007) asks for images of the front and back of the applicant's Aadhaar card as separate
mandatory uploads. Until now the rule was a masked Aadhaar (first 8 digits covered), so the full number
was never stored. The owner decided that the uploaded images must be full and unmasked: every detail on
the card must be visible to the reviewer. UIDAI rules restrict who may store Aadhaar numbers and copies,
and may require an Aadhaar Data Vault; we have no legal answer yet (Q-20).

## Decision

- The upload kinds are `AADHAAR_FRONT` and `AADHAAR_BACK`, full images (JPEG, PNG or PDF; C-9).
- **Bytes:** encrypted like every file (AES-256-GCM, ADR-0006), never in MySQL and never logged.
- **No extraction:** no OCR, and the Aadhaar number is never read out of the image or stored as a field. The only Aadhaar value in the database stays the masked one from DigiLocker.
- **Staff access:** only the reviewer who has claimed the case, or an approver, can open these files.
- **Audit:** every view is audited with the full document id (`DOCUMENT_VIEWED`).
- **Caching:** files are served with `Cache-Control: no-store`.
- **Retention:** follows Q-5 once decided. Until then the files are kept like other documents.

## Options considered

| Option | Pros | Cons |
|---|---|---|
| Masked images only (previous rule) | no full numbers stored | the owner wants every detail visible |
| Full images with the controls above (chosen) | reviewer sees the whole card | legal question open; a breach would expose full Aadhaar numbers |
| Full images in a separate Aadhaar Data Vault | the strongest compliance posture | needs infrastructure and a legal reading first (Q-20) |

## Consequences

- `docs/SECURITY.md` no longer says the full Aadhaar number is never stored: it can be inside these images.
- Production use waits for Q-20. If a Data Vault is required, these kinds move to a separate `DocumentStorage` with its own keys.
