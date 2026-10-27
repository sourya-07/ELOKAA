# Open questions

Unknowns live here, never as silent assumptions in code. "Default" is what the code does until the owner decides; changing a default is a settings or code change recorded here.

Status: **open** (needs a decision), **default** (we build on the default, confirmation welcome), **closed**.

## Blockers for production (building can continue)

| ID | Question | Status | Default / note |
|---|---|---|---|
| Q-1 | Which credit bureau provider, and what is our legal route to credit data (Credit Information Companies (Regulation) Act 2005, consent wording)? | open | `CreditBureau` has only the local fake and the "not configured" stub (ADR-0004, ADR-0005) |
| Q-2 | Show the CIBIL score to the applicant, or only the outcome? | open | outcome only |
| Q-3 | Liveness / face-match provider? EPS has none. | open | live photo is captured in the browser and reviewed by staff; it is not proof of liveness |
| Q-4 | Hosting target, object storage, staging host | open | encrypted file volume (ADR-0006); deploy jobs are stubs |
| Q-5 | Retention period per data type; when are documents of ineligible, withdrawn, expired and rejected applications deleted? | open | to be set before production; masked values and audit records stay |
| Q-6 | Can an ineligible or rejected applicant apply again, and after how long? | open | no new application while one is live; closed ones do not block |

## Built on a default (confirm or change)

| ID | Question | Status | Default |
|---|---|---|---|
| Q-7 | OTP delivery | default | EPS Mobile/OTP API through `OtpSender` (no DLT setup on our side) |
| Q-8 | PAN, DigiLocker, voter ID, driving licence checks | default | EPS PAN Lite/Advanced, EPS DigiLocker, EPS voter ID and DL APIs; staff review on top |
| Q-9 | Manual (step-by-step) Aadhaar | default | masked Aadhaar image (first 8 digits covered) + last 4 digits, checked by staff. The full number is never stored. Aadhaar OTP eKYC needs a licensed KUA/AUA and is not planned |
| Q-10 | Who is the partner after approval? Provisioning? | default | an individual partner; approval ends this project and publishes `PartnerApproved`; no account is created |
| Q-11 | Staff console in scope? | default | yes, a minimal one ported from eloka (queue, case, decisions) |
| Q-12 | File formats | default | photos JPEG (.jpg/.jpeg) only, 5 MB; certificates and ID images JPEG or PDF, 10 MB. The whiteboard may also say PNG |
| Q-13 | Malware scanning | default | ClamAV container in staging and production |
| Q-14 | Java base package | default | `in.eko.eloka.partner` |
| Q-15 | Voter ID and driving licence: may an applicant add both? | default | yes; at least one is required |
| Q-16 | Optional computer certificates: how many? | default | up to 5 |

## Closed

| ID | Decision | Date |
|---|---|---|
| C-1 | New partner journey, in this repo, porting proven eloka parts (ADR-0001) | 2026-10-27 |
| C-2 | CIBIL check right after PAN is verified (ADR-0005) | 2026-10-27 |
| C-3 | New `PTN-` Stitch screens in the ELOKA Platform project; Local Admin `APP-` screens unchanged | 2026-10-27 |
| C-4 | LEAD process out of scope | 2026-10-27 |
