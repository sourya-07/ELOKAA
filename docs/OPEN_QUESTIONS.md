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
| Q-6 | Can an ineligible or rejected applicant apply again, and after how long? | open | an open, approved or ineligible application blocks a new one for the same mobile or PAN (so a failed credit check cannot be retried); rejected, withdrawn and expired ones do not |
| Q-20 | Legal basis and controls for storing **full, unmasked** Aadhaar images (owner decision 2026-10-28; UIDAI rules on storing Aadhaar copies, Aadhaar Data Vault) | open | built with encrypted storage, restricted and audited staff viewing, no OCR (ADR-0008); not for production until answered |
| Q-21 | Which SMTP service and sending domain for email codes (SPF, DKIM, DMARC)? | open | fake sender locally; "not configured" elsewhere |
| Q-30 | Should the server refuse redoing a verified PAN, voter ID / driving licence or DigiLocker step (unless staff reopened it)? Today the client hides "Change" for them, but the API still accepts a redo (a different PAN runs the credit check again) | open | client read-only only; the server is unchanged until decided (a business-rule change) |
| Q-31 | Reverse geocoding (an address from the live photo's coordinates) and IP / VPN / proxy risk signals: which provider, if any? Both send applicant data to a third party, and a VPN check gives false positives (mobile carrier networks) | open | none built: the applicant and staff see the browser-reported coordinates only; nothing blocks on location risk |
| Q-32 | Translations: who translates and reviews Punjabi, Hindi, Urdu, Gujarati, Bengali and Tamil? Consent and declaration texts must be legally approved in each language (and the consent record should then keep the language). Staff console in English only? | open | the selector lists all seven; only English (UK) can be chosen until a reviewed, complete catalog exists; consent, declaration and staff console stay English; not every page is in the catalog yet |
| Q-33 | May an applicant withdraw while staff are reviewing (`UNDER_REVIEW`, `ON_HOLD`)? Should reject and hold need a second person, as approve does? Should staff see only some applications (region, assignment)? | open | no withdrawal during review; one approver rejects; any staff role can hold; every staff member sees every application (a state-machine and role change each, if decided) |
| Q-34 | Status notifications: should submit, correction, approval and rejection send an email and/or SMS? Needs the SMTP account (Q-21) and an SMS template (DLT) | open | nothing is sent; the pages say "check this page for updates" |
| Q-35 | Product name: the pages say "ELOKA"; the brief says "ELOKAA". Which is right? | open | "ELOKA" (one catalog change once decided) |
| Q-22 | EPS DigiLocker: can it return PAN, voter ID or driving licence (`document_requested`)? Which codes mean "cancelled" or "no Aadhaar in DigiLocker"? (with Q-18) | open | Aadhaar only; unknown codes are technical errors, never a pass or a used attempt |

## Built on a default (confirm or change)

| ID | Question | Status | Default |
|---|---|---|---|
| Q-7 | OTP delivery | default | EPS Mobile/OTP API through `OtpSender` (no DLT setup on our side) |
| Q-8 | PAN, DigiLocker, voter ID, driving licence checks | default | EPS PAN Lite, EPS DigiLocker (Aadhaar), EPS Voter ID and Driving Licence APIs plus EPS Name Match (built in journey v2; before it, voter ID and DL were only typed and checked by staff); staff review on top. Mock-tested only, not yet on EPS UAT |
| Q-10 | Who is the partner after approval? Provisioning? | default | an individual partner; approval ends this project and publishes `PartnerApproved`; no account is created |
| Q-11 | Staff console in scope? | default | yes, a minimal one ported from eloka (queue, case, decisions) |
| Q-13 | Malware scanning | default | ClamAV container in staging and production |
| Q-14 | Java base package | default | `in.eko.eloka.partner` |
| Q-15 | Voter ID and driving licence: may an applicant add both? | default | one verified document is required (`ALTERNATE_ID`); its two sides are uploaded |
| Q-16 | Optional computer certificates: how many? | default | up to 5 |
| Q-18 | EPS DigiLocker: the document call needs a `verification_id` the create call does not return | default | we send our own id as `client_ref_id` on create and `verification_id` on fetch; untested against EPS UAT |
| Q-19 | A person with no credit history (no CIBIL score) | default | `UNABLE_TO_ASSESS` (`NO_CREDIT_HISTORY`): never eligible, the step blocks after 3 tries for staff follow-up |
| Q-23 | Name-match threshold between the voter ID / driving licence name and the DigiLocker (Aadhaar) name | default | `eloka.verification.name-match-min-score: 0.85` (EPS calls 0.85–0.99 a good partial match); to tune with EPS UAT samples |
| Q-24 | Live-photo location: accuracy limits, applicants outside India | default | built: refused above 1000 m (`eloka.onboarding.location.max-accuracy-m`); flagged for staff above 100 m (`flag-accuracy-m`) and outside India; location asked just before the photo, not at the start |
| Q-25 | One email per live application? (refusing reveals that an address is registered) | default | yes, with a neutral message |
| Q-26 | Must the uploaded voter ID / driving licence images be of the document that was verified? | default | built: yes (`KYC_DOCUMENTS` submit) |
| Q-28 | Adopt the `DESIGN.taste.md` proposal (Geist, Ledger Blue)? | default | no; `DESIGN.md` (Inter, Connect Blue) stays |
| Q-29 | An applicant under 18 at DigiLocker: block the step, or close the application? May they apply after turning 18? | default | built: the Aadhaar step blocks at once (`DIGILOCKER_UNDER_AGE`) and the application expires; no permanent bar |
| Q-17 | EPS Mobile OTP needs a `csp_id` ("the agent the OTP is sent on behalf of"). What do we send for a partner applicant? | default | `ELOKA_EPS_CSP_ID`, else our `initiator_id`. The EPS adapter is tested against a mock server only, not yet against EPS UAT |

## Closed

| ID | Decision | Date |
|---|---|---|
| C-1 | New partner journey, in this repo, porting proven eloka parts (ADR-0001) | 2026-10-27 |
| C-2 | CIBIL check right after PAN is verified (ADR-0005) | 2026-10-27 |
| C-3 | New `PTN-` Stitch screens in the ELOKA Platform project; Local Admin `APP-` screens unchanged | 2026-10-27 |
| C-4 | LEAD process out of scope | 2026-10-27 |
| C-5 | Identity through DigiLocker only; the step-by-step (masked Aadhaar) path is retired (was Q-9) (ADR-0007) | 2026-10-28 |
| C-9 | File rules (owner, 2026-10-29, was Q-12): documents (Aadhaar, PAN, voter ID, driving licence, marksheets, graduation, diploma, computer certificates) JPG/JPEG, PNG or PDF, 10 KB to 5 MB; photos JPG/JPEG or PNG, 20 KB to 2 MB (and at least 300 px a side); at most 5 computer certificates. PNG is stored re-encoded as JPEG | 2026-10-29 |
| C-8 | Graduation certificate required; diploma certificate optional (was: either one) | 2026-10-29 |
| C-7 | Start page time estimate: "about 3–5 minutes" (owner's decision; the whole journey with DigiLocker, the credit check and the uploads may take longer, so measure it on EPS UAT) | 2026-10-29 |
| C-6 | Official DigiLocker sign-up link: `digilocker.gov.in/web/signup` (checked on digilocker.gov.in; was Q-27). Since 2026-10-29 "Don't have a DigiLocker account?" opens it directly in a new tab (no help panel, no app-store links) | 2026-10-28 |
