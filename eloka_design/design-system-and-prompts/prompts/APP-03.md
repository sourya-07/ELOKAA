---
page: APP-03
device: MOBILE (then DESKTOP)
---
The application hub: the home page an applicant sees while their onboarding application is in progress. It shows where they are, what's next, and every step's status.

**PLATFORM:** Web, Mobile-first. Desktop adds a vertical step rail on the left and a 720px content column.

**PAGE STRUCTURE:**
1. **Top bar:** "ELOKA" wordmark on the left; "Help" and "Sign out" on the right.
2. **Header:**
   - Title "Your application".
   - A copyable application ID chip "onb_7Q2K9X" and a status pill "In progress".
   - A progress line "Step 4 of 13" with a thin progress bar.
3. **Next step card:** Highlighted card "Next: Business PAN" with one line, "Verify your business PAN to continue", and a primary button "Continue".
4. **Expiry notice** (variant): a small warning strip "This application will expire on 28 Oct if there's no activity."
5. **Correction banner** (variant): a warning card "Changes requested by our team — fix 2 items by 5 Nov" with a "View changes" button.
6. **Step list:** All 13 steps in order:
   1. Mobile verification
   2. Consent
   3. Program
   4. Business PAN
   5. Business details
   6. Email
   7. Signatory KYC
   8. Live photo
   9. Documents
   10. Bank account
   11. White-label setup
   12. Declaration
   13. Agreement

   Each row has a status icon, the step name and a status pill ("Completed", "In progress", "Not started", "Failed — 1 attempt left", "Locked", "Needs changes" with a short reviewer note). Tapping a row opens that step.
7. **Secondary action:** A ghost text button "Withdraw application" at the bottom.

**States:**
- Loading: a skeleton of the header plus 6 list rows.
- A blocked step shows "Locked after too many attempts — contact support", and it has no Continue.
