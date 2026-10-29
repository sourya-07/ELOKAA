# Error catalog

Every error `code` the server can send, where it comes from, and what the applicant (or staff member) is told.
Only codes that exist in the server code are listed: `ErrorCatalogTest` (server) fails when the code has a code
that is missing here, or this page lists one the server cannot send; `errors.test.ts` (client) fails when a code
here has no message in `client/src/lib/errors.ts`. Change all three together.

Every error answer is `application/problem+json` with `status`, `code` and `detail` (docs/API.md). The client shows
its own message for the code (so it can be translated), falling back to the server's `detail`. Messages never
contain stack traces, internal names, tokens or personal data, and never promise that trying again will work
when it may not.

Class: **validation** (the request is wrong; fix it), **auth** (sign in again or not allowed), **not-found**,
**conflict** (the state does not allow it now), **rate-limit** (wait; `retry_after` in seconds), **provider** (an
outside service failed or is not set up; nothing was used up), **server** (unexpected; logged with the request id).

## HTTP errors

| Code | HTTP | Where | Class | Message (client) | What to do |
|---|---|---|---|---|---|
| `VALIDATION_FAILED` | 422 | any write | validation | the field message from `invalid_params`, else "Check the details you entered." | correct the field shown |
| `MALFORMED_REQUEST` | 400 | any route (body not readable, wrong type of value) | validation | "Something in the request was not right. Please reload the page and try again." | reload |
| `METHOD_NOT_ALLOWED` | 405 | any route | validation | as above | reload |
| `UNSUPPORTED_MEDIA_TYPE` | 415 | any route | validation | as above | reload |
| `NOT_ACCEPTABLE` | 406 | any route | validation | as above | reload |
| `REQUEST_REJECTED` | 4xx | any other framework refusal | validation | as above | reload |
| `IDEMPOTENCY_KEY_REQUIRED` | 400 | submit, withdraw, PAN, credit check, alternate ID, email verify | validation | as above | reload (the client always sends one) |
| `IDEMPOTENCY_KEY_REUSED` | 422 | same | validation | as above | reload |
| `PRECONDITION_REQUIRED` | 428 | application writes without `If-Match` | validation | as above | reload |
| `PRECONDITION_FAILED` | 412 | application writes with a stale `If-Match` (+`current` snapshot) | conflict | "Your application changed in another window. The latest version is shown." | continue from the latest |
| `UNAUTHENTICATED` | 401 | every signed-in route | auth | "Please sign in again." | sign in |
| `SESSION_EXPIRED` | 401 | session refresh | auth | "Your session has ended. Please sign in again." | sign in |
| `FORBIDDEN` | 403 | wrong session kind or role | auth | "You are not allowed to do this." | — |
| `NOT_STAFF` | 403 | staff sign-in | auth | "This account is not an ELOKA staff account." | use a staff account |
| `SAME_PERSON` | 403 | staff approve | auth | "The person who recommended cannot also approve." | another approver |
| `NOT_ASSIGNED` | 403 | staff review actions, full Aadhaar images | auth | "Claim the case first." | claim it |
| `OTP_INVALID` | 422 | mobile, email and withdraw codes (+`attempts_left`) | validation | "The code is not correct. N attempts left." | type it again |
| `OTP_EXPIRED` | 410 | same | conflict | "This code has expired, or was already used. Ask for a new one." | send a new code |
| `OTP_LOCKED` | 429 | same | rate-limit | "Too many wrong codes. Ask for a new code." | send a new code |
| `OTP_LIMIT_REACHED` | 429 | sending a code (+`retry_after`) | rate-limit | "Too many codes asked for. You can ask again in N." | wait for the countdown |
| `DIGILOCKER_LIMIT_REACHED` | 429 | DigiLocker start (10 per hour) | rate-limit | "DigiLocker was started too many times. Please try again in an hour." | wait |
| `APPLICATION_NOT_FOUND` | 404 | an application id that is not the caller's | not-found | "We could not find this application." | open "My application" |
| `NO_APPLICATION` | 404 | current application | not-found | "You have not started an application yet." | start one |
| `APPLICANT_NOT_FOUND` | 404 | withdraw code | not-found | "Please sign in again." | sign in |
| `DOCUMENT_NOT_FOUND` | 404 | a file that is not the caller's, or replaced | not-found | "This file is no longer available." | reload |
| `NOT_FOUND` | 404 | staff record | not-found | "Not found." | — |
| `STEP_NOT_FOUND` | 404 | staff unblock | not-found | "This application has no such step." | — |
| `ADDRESS_NOT_FOUND` | 404 | staff address decision | not-found | "This application has no address yet." | — |
| `EVIDENCE_NOT_FOUND` | 404 | staff evidence decision | not-found | "This check was not found." | reload |
| `NOTHING_TO_SHOW` | 404 | staff reveal | not-found | "There is no value to show." | — |
| `FILE_TOO_LARGE` | 413 | upload above the request limit | validation | "The file is too large. Files can be at most 10 MB." | a smaller file |
| `DUPLICATE_FILE` | 422 | upload | validation | "You already uploaded this same file for another document. Choose the right file." | choose another file |
| `LOCATION_REQUIRED` | 422 | live photo | validation | "The live photo needs your location. Allow location for this site and take the photo again." | allow location |
| `LOCATION_INVALID` | 422 | live photo | validation | "We could not read your location. Take the photo again." | take it again |
| `LOCATION_TOO_INACCURATE` | 422 | live photo (+`accuracy_m`) | validation | "Your location is too rough. Turn on precise location or GPS, then take the photo again." | turn on GPS |
| `LOCATION_STALE` | 422 | live photo | validation | "This location is not from now. Take the photo again." | take it again |
| `STEP_BLOCKED` | 423 | a step with no tries left | conflict | "Too many failed attempts on this step. Our team will contact you." | wait for our team |
| `CONFLICT` | 409 | two changes at the same moment | conflict | "This was changed at the same moment somewhere else. Please reload and try again." | reload |
| `ALREADY_APPROVED` | 409 | start | conflict | "You are already an approved ELOKA partner." | — |
| `APPLICATION_NOT_EDITABLE` | 409 | any write after submitting or closing | conflict | "Your application can no longer be changed." | see the status page |
| `STEP_NOT_EDITABLE` | 409 | a step frozen during a correction | conflict | "This part cannot be changed now." | change only what our team asked for |
| `INVALID_STATE_TRANSITION` | 409 | withdraw, staff actions (+`current_status`) | conflict | "This cannot be done in the application's current status." | reload |
| `PREREQUISITE_INCOMPLETE` | 409 | a step before its earlier steps (+`missing_steps`) | conflict | "Finish the earlier steps first." | follow "Continue" |
| `CONSENT_REQUIRED` | 409 | DigiLocker, credit check (+`purpose`) | conflict | "Tick the consent box first." | give consent |
| `CONSENT_VERSION_OUTDATED` | 409 | consent (+`current_version`) | conflict | "The consent text changed. Please reload and read it again." | reload |
| `DECLARATION_VERSION_OUTDATED` | 409 | declaration | conflict | "The declaration changed. Please reload and read it again." | reload |
| `NOT_ELIGIBLE` | 409 | submit | conflict | "The credit check has not been passed." | — |
| `DOCUMENTS_MISSING` | 409 | step submit, application submit (+`missing_documents`) | conflict | "Add your …" (from `missing_documents`) | upload the files named |
| `DOCUMENT_REQUIRED` | 409 | removing an Aadhaar image | conflict | "This file is required. Upload a new one to replace it." | replace it |
| `TOO_MANY_DOCUMENTS` | 409 | a sixth computer certificate | conflict | "You can add up to 5 computer certificates." | remove one first |
| `EMAIL_ALREADY_USED` | 409 | email code | conflict | "Please use a different email address." | another email |
| `ADDRESS_FROM_DIGILOCKER` | 409 | typing an address DigiLocker already gave | conflict | "Your address came from DigiLocker and cannot be typed over." | — |
| `ID_TYPE_EXHAUSTED` | 409 | alternate ID (3 tries of this type used) | conflict | "No tries are left for this document. Use the other one." | the other document |
| `AADHAAR_NOT_VERIFIED` | 409 | PAN, alternate ID before Aadhaar | conflict | "Verify your Aadhaar through DigiLocker first." | DigiLocker |
| `DIGILOCKER_ALREADY_USED` | 409 | DigiLocker return used twice | conflict | "This DigiLocker link was already used." | start again |
| `DIGILOCKER_EXPIRED` | 410 | DigiLocker return after 15 minutes | conflict | "This DigiLocker link has expired. Please start again." | start again |
| `IDEMPOTENCY_IN_PROGRESS` | 409 | the same action sent twice at once | conflict | "This is still being done. Please wait a moment." | wait |
| `STEP_NOT_BLOCKED` | 409 | staff unblock | conflict | "This step is not blocked." | reload |
| `EVIDENCE_NOT_DECIDABLE` | 409 | staff evidence decision | conflict | "This check cannot be decided by hand." | — |
| `NOT_READY` | 409 | staff recommend or approve (+`approval_blockers`) | conflict | "Some items are not verified yet." | verify them |
| `NO_RECOMMENDATION` | 409 | staff approve | conflict | "A reviewer must recommend approval first." | — |
| `PROVIDER_NOT_CONFIGURED` | 409 | a check whose service is not set up here | provider | "This check is not available right now. Our team has been told." | try later |
| `PROVIDER_UNAVAILABLE` | 503 | an outside service did not answer | provider | "A service we use is not answering. Nothing was used up; please try again in a few minutes." | try later |
| `INTERNAL_ERROR` | 500 | anything unexpected | server | "Something went wrong on our side. Please try again." | try again; if it repeats, contact support |

## Step results (`last_error_code`)

A failed check does not answer with an error status: the step's `last_error_code` says why (docs/API.md). A
"technical" one used no attempt.

| Code | Step | Class | Message (client) | Attempt used |
|---|---|---|---|---|
| `DIGILOCKER_INVALID_REFERENCE` | AADHAAR | provider | "DigiLocker did not finish. Please try again." | yes |
| `DIGILOCKER_NO_AADHAAR_DATA` | AADHAAR | validation | "Your DigiLocker has no Aadhaar linked. Link your Aadhaar in DigiLocker, then try again." | yes |
| `DIGILOCKER_UNAVAILABLE` | AADHAAR | provider | "DigiLocker is not answering right now. Nothing was used up; check again in a few minutes." | no |
| `DIGILOCKER_UNDER_AGE` | AADHAAR | validation | "You must be 18 or older to become an ELOKA partner." | blocks |
| `PAN_NAME_MISMATCH` | PAN | validation | "The name does not match the PAN records. Use your name exactly as on your PAN card." | yes |
| `PAN_DOB_MISMATCH` | PAN | validation | "The date of birth does not match the PAN records." | yes |
| `PAN_INVALID_PAN` | PAN | validation | "This PAN is not valid. Check the number and try again." | yes |
| `PAN_ALREADY_USED` | PAN | conflict | "This PAN is already used in another application." | yes |
| `CREDIT_SCORE_BELOW_THRESHOLD` | CREDIT_CHECK | conflict | "Your credit check did not meet our requirement." | ends the application |
| `UNABLE_TO_ASSESS` | CREDIT_CHECK | provider | "We could not complete the credit check. Our team will contact you." | yes |
| `BUREAU_UNAVAILABLE` | CREDIT_CHECK | provider | "We could not complete the credit check. Please try again later." | yes |
| `NO_CREDIT_HISTORY` | CREDIT_CHECK | validation | "We could not find a credit history for you. Our team will contact you." | yes |
| `INVALID_SCORE` | CREDIT_CHECK | provider | "We could not complete the credit check. Our team will contact you." | yes |
| `VOTER_ID_NOT_FOUND` | ALTERNATE_ID | validation | "No voter ID with this number was found. Check the number on your card." | yes |
| `VOTER_ID_NAME_MISMATCH` | ALTERNATE_ID | validation | "The name on this voter ID does not match your Aadhaar." | yes |
| `VOTER_ID_DOB_MISMATCH` | ALTERNATE_ID | validation | "The date of birth on this voter ID does not match your Aadhaar." | yes |
| `VOTER_ID_NOT_ACTIVE` | ALTERNATE_ID | validation | "This voter ID is not active." | yes |
| `DRIVING_LICENCE_NOT_FOUND` | ALTERNATE_ID | validation | "No driving licence with this number was found. Check the number on your licence." | yes |
| `DRIVING_LICENCE_NAME_MISMATCH` | ALTERNATE_ID | validation | "The name on this driving licence does not match your Aadhaar." | yes |
| `DRIVING_LICENCE_DOB_MISMATCH` | ALTERNATE_ID | validation | "The date of birth on this driving licence does not match your Aadhaar." | yes |
| `DRIVING_LICENCE_NOT_ACTIVE` | ALTERNATE_ID | validation | "This driving licence is not active (for example, it has expired)." | yes |
| `ID_ALREADY_USED` | ALTERNATE_ID | conflict | "This document is already used in another application." | yes |
| `ALTERNATE_ID_UNAVAILABLE` | ALTERNATE_ID | provider | "The government record could not be checked right now. Nothing was used up; please try again in a few minutes." | no |

The snapshot's `submit_blockers[].code` is one of these, or `STEP_NOT_DONE`, `STEP_BLOCKED`, `STEP_FAILED`,
`STEP_REOPENED`, `STEP_PENDING`, `DOCUMENTS_MISSING` (the review page words them, `client/src/lib/blockers.ts`).
Staff `approval_blockers` (`CREDIT_CHECK_NOT_PASSED`, `<KIND>_NOT_VERIFIED`, `<STEP>_NOT_SUBMITTED`, …) are shown to
staff as they are.

## Not known yet

EPS answer codes are mapped only where the adapter maps them (`EpsDigiLocker`: 327 pending, 463 invalid reference);
any other EPS answer is `PROVIDER_UNAVAILABLE`, never a pass and never a used attempt. Which EPS codes mean
"cancelled" or "no Aadhaar in DigiLocker" is open (docs/OPEN_QUESTIONS.md Q-22).
