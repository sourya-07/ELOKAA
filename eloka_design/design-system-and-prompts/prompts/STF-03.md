---
page: STF-03
device: DESKTOP
---
The staff case view for one application. A reviewer checks the business details, verifications and documents, then acts, and an approver makes the final decision.

**PLATFORM:** Web, Desktop-first (1280px), using the same sidebar as the queue.

**PAGE STRUCTURE:**
1. **Header:**
   - A back link "Applications".
   - The title "Sample Traders Pvt Ltd", with a monospace ID chip "onb_7Q2K9X" and the status pill "Under review".
   - The meta line "Private Limited · Submitted 21 Oct 2026 · Assigned to you".
   - Actions on the right: "Release" (secondary). An alternative state shows "Claim case" as the primary action.
2. **Two-column body:** a main column of about 860px and a right column of 320px.
3. **Main column sections, as cards:**
   - **"Flags":** a warning card listing "Business name differs from PAN record" and "Bank account holder differs", each with a one-line explanation.
   - **"Business profile":** a two-column label/value list showing Business name, Company type, Signatory, Contact mobile XXXXXX3210, Email s••••@sampletraders.in, Address, GSTIN, CIN. The PAN shows masked as XXXXX1234X, with a small "Reveal" text button.
   - **"Verifications":** a table with the columns Check (PAN, Email, DigiLocker KYC, Bank account), Status pill (Succeeded / Needs review / Failed), Name match score ("92", "71"), and an "Accept / Reject" action pair on the row that needs review.
   - **"Documents":** document tiles (Address proof — "Waiting for review" with View, Verify, Reject; Certificate of incorporation — "Verified"; Bank proof — "Rejected" with the reviewer note).
   - **"History":** a compact timeline of review actions with actor, time and action.
4. **Right column, a sticky decision panel:**
   - A card "Decision".
   - A warning list "Can't approve yet": "Documents not verified (1)", "Bank account needs a decision", "No approval recommendation from a reviewer".
   - Buttons stacked:
     - "Request changes" (secondary)
     - "Recommend approval" and "Recommend rejection" (secondary)
     - "Approve" (primary; disabled while blockers exist)
     - "Reject" (danger outline)
     - "Put on hold" (ghost)
   - A note: "You can't approve a case you claimed or recommended."
5. **States:**
   - Read-only variant for auditors: no action buttons, and a neutral note "Read-only access".
   - Not-claimed variant: the actions are disabled, with the note "Claim this case to act on it".
