# ELOKA designs (Google Stitch)

All UI designs made in Google Stitch for ELOKA. Stitch project: `ELOKA Platform` (id 18017913531452476583). Copied 27 Oct 2026.

Each design has two files: a **.png picture** (open to look at it) and an **.html page** (open in a browser to see the real layout).

## Folders

| Folder | What is in it |
|---|---|
| `final/` | The designs we use (58), the same set kept in the eloka repo at `client/.stitch/designs`. Grouped by area. |
| `design-system-and-prompts/` | `DESIGN.md` (colours, fonts, spacing), `SITE.md`, the prompts used to make the screens, and `metadata.json` (screen ids and notes). |

Names: `APP-` = partner application steps, `STF-` = staff console, `R1-` = existing tenant app reskin, `DS-00` = design system sheet. `-desktop` / `-phone` = screen size.

## final/1-design-system: Design system sheet (1)

| Design | Notes |
|---|---|
| DS-00 | approved 2026-10-21 (variant A). Sample text names real providers; copy comes from docs/frontend/UI_UX_PLAN.md, not from this sheet. |

## final/2-partner-application: Partner (Local Admin) application (35)

| Design | Notes |
|---|---|
| APP-01 | Page 1: entry + choose how to apply, with 'Faster', '6 steps' and '13 steps', no time estimate. Switched 2026-10-22 to this copy (the other lacked the step counts). Awaiting owner review (ST-8b). |
| APP-01-desktop | Desktop page 1, 2026-10-22: the two path cards side by side with 'Faster' and step counts, no time estimate. Awaiting owner review (ST-8b). LOCAL FIX 2026-10-22: the committed HTML/PNG were corrected by hand (Stitch edits did not save) and re-rendered with headless Chrome, so the Stitch copy is behind; the repo file is the spec. |
| APP-01-manual | Page 1 with 'Apply step by step' selected. Step-by-step variant made locally on 2026-10-22 from the APP-01 Stitch HTML (owner: the step-by-step walkthrough must show 'Apply step by step' selected); rendered with headless Chrome, no Stitch id. Awaiting owner review (ST-8b). |
| APP-01-manual-desktop | Page 1 desktop with 'Apply step by step' selected. Step-by-step variant made locally on 2026-10-22 from the APP-01-desktop Stitch HTML (owner: the step-by-step walkthrough must show 'Apply step by step' selected); rendered with headless Chrome, no Stitch id. Awaiting owner review (ST-8b). |
| APP-02 | Page 2 of both paths: the partner's mobile + OTP, with the chosen-path chip. Revised 2026-10-22 for the owner's rules (mobile is page 2 of both paths, no time estimates, home + business address, nothing asked twice). Awaiting owner review (ST-8b). The '(Error Variant)' label is Stitch's, not a spec. |
| APP-02-desktop | Desktop page 2 (both paths): mobile number and code side by side, with the chosen-path chip. Generated 2026-10-22. Awaiting owner review (ST-8b). |
| APP-02-manual | Page 2 with the chip 'Applying step by step'. Step-by-step variant made locally on 2026-10-22 from the APP-02 Stitch HTML (owner: the step-by-step walkthrough must show 'Apply step by step' selected); rendered with headless Chrome, no Stitch id. Awaiting owner review (ST-8b). |
| APP-02-manual-desktop | Page 2 desktop with the chip 'Applying step by step'. Step-by-step variant made locally on 2026-10-22 from the APP-02-desktop Stitch HTML (owner: the step-by-step walkthrough must show 'Apply step by step' selected); rendered with headless Chrome, no Stitch id. Awaiting owner review (ST-8b). |
| APP-03 | generated 2026-10-21, awaiting owner review (ST-8b) |
| APP-03-desktop | generated 2026-10-21, awaiting owner review (ST-8b); desktop layout with the 13-step rail |
| APP-04-05 | generated 2026-10-21, awaiting owner review (ST-8b) |
| APP-04-desktop | Desktop consent step (step 2) with the 13-step rail, 2026-10-22. Program (step 3) has no desktop version; it follows the same layout. Awaiting owner review (ST-8b). |
| APP-06 | generated 2026-10-21, awaiting owner review (ST-8b) |
| APP-06-desktop | Created in Stitch by the owner from the hand-off prompts (2026-10-22) and checked against the spec. Awaiting owner review (ST-8b). Repo copy edited: removed 'NSDL / Income Tax integration', 'SPEC VIEW', the '< 3 seconds' estimate and the error codes. |
| APP-07 | Business details, phone: name from PAN, mobile from sign-in, home address, business address with 'Same as home address', signature JPG/PNG/PDF up to 10 MB. Switched 2026-10-22 to this copy (the other had 'Rahul Sharma' and 'max 2MB'). Awaiting owner review (ST-8b). |
| APP-07-desktop | Created in Stitch by the owner from the hand-off prompts (2026-10-22) and checked against the spec. Awaiting owner review (ST-8b). Only copy with every field right (home + business address, mobile from sign-in, name from PAN, 10 MB). |
| APP-08-10 | generated 2026-10-21, awaiting owner review (ST-8b) |
| APP-09-desktop | Desktop signatory KYC with DigiLocker (step 7), 2026-10-22. Awaiting owner review (ST-8b). Not specs: 'Need assistance?' link; the manual option is APP-09B. |
| APP-09B | Created in Stitch by the owner from the hand-off prompts (2026-10-22) and checked against the spec. Awaiting owner review (ST-8b). Manual signatory KYC, phone. Backend gap FQ-19. |
| APP-09B-desktop | Created in Stitch by the owner from the hand-off prompts (2026-10-22) and checked against the spec. Awaiting owner review (ST-8b). Repo copy edited: removed the invented company footer. Backend gap FQ-19. |
| APP-11 | Documents, phone, 2026-10-22: no PAN card tile (PAN already verified). Awaiting owner review (ST-8b). 'STEP 9 OF 13' in capitals is Stitch's; the build uses sentence case. |
| APP-11-12 | generated 2026-10-21, awaiting owner review (ST-8b) SUPERSEDED IN PART 2026-10-22: the 'PAN card' optional tile is not a spec (PAN is already verified) and the bank frame is replaced by APP-12. |
| APP-11-desktop | Desktop documents, 2026-10-22: no PAN card tile. Awaiting owner review (ST-8b). Not specs: '13 Steps Remaining', 'Resume Later', '69% completed'. |
| APP-12 | Bank account, phone: IFSC only (bank shown from it), account number once, no confirm field. Revised 2026-10-22 for the owner's rules (mobile is page 2 of both paths, no time estimates, home + business address, nothing asked twice). Awaiting owner review (ST-8b). The '77% completed' label is Stitch's. |
| APP-12-desktop | Created in Stitch by the owner from the hand-off prompts (2026-10-22) and checked against the spec. Awaiting owner review (ST-8b). Repo copy edited: removed the invented 'Expected legal entity…' line. |
| APP-13 | Phone layout, generated 2026-10-22 because the desktop one was squeezed in the Stitch phone preview. Awaiting owner review (ST-8b). Not specs: swatches are drawn as gradients (the build shows primary + accent halves), and the 'Navigation style' label and the 'Custom' option position differ from the prompt. |
| APP-13-desktop | Desktop layout (form + sticky preview), generated 2026-10-21, awaiting owner review (ST-8b) |
| APP-14 | Review + declaration, phone, with Home address and Business address cards. Revised 2026-10-22 for the owner's rules (mobile is page 2 of both paths, no time estimates, home + business address, nothing asked twice). Awaiting owner review (ST-8b). |
| APP-14-15 | generated 2026-10-21, awaiting owner review (ST-8b) 2026-10-22: the review frame is replaced by APP-14; the agreement frame still applies. |
| APP-14-desktop | Created in Stitch by the owner from the hand-off prompts (2026-10-22) and checked against the spec. Awaiting owner review (ST-8b). |
| APP-15-desktop | Regenerated 2026-10-22 (ST-7): the rail shows the 13 real steps; the PDF preview is placeholder lines (the real text is the server PDF). Awaiting owner review (ST-8b). 'ELOKA v3.4.2' and 'Page 1 of 4' are Stitch's, not specs. |
| APP-16-21 | generated 2026-10-21, awaiting owner review (ST-8b) |
| APP-16-desktop | Generated 2026-10-22 by converting the existing screen. Awaiting owner review (ST-8b). Desktop 'Application submitted / under review'. Not spec: the activity timeline entries are Stitch's; the build shows the real audit events. |
| APP-22 | DigiLocker quick path: home address from DigiLocker, business address only if not found, mobile from sign-in. Revised 2026-10-22 for the owner's rules (mobile is page 2 of both paths, no time estimates, home + business address, nothing asked twice). Awaiting owner review (ST-8b). |
| APP-22-desktop | Created in Stitch by the owner from the hand-off prompts (2026-10-22) and checked against the spec. Awaiting owner review (ST-8b). Repo copy edited: header 'Tenant Onboarding' → 'ELOKA', removed Dashboard / Support / Exit. |

## final/3-staff-console: Eko staff console (4)

| Design | Notes |
|---|---|
| STF-01 | generated 2026-10-21, awaiting owner review (ST-8c) |
| STF-02 | Regenerated 2026-10-22 (ST-7): no '+ New application' button, no sidebar subtitle, status and flag pill tones fixed. Awaiting owner review (ST-8c). The empty-state helper line is Stitch's, not a spec. |
| STF-03 | Refined 2026-10-22 (ST-7): sidebar has only Queue and Assigned to me, email masked, Flags card in the warning tone, invented ID label removed. Awaiting owner review (ST-8c). |
| STF-04-11 | Full staff dialog sheet (document review, verification decision, corrections, recommend, approve / reject / hold, PII reveal, audit trail, permission cards). Switched 2026-10-22 from eb81bb55, which was cut off and had invented sidebar items. Awaiting owner review (ST-8c). |

## final/4-tenant-app: Tenant app (agents and admins) (18)

| Design | Notes |
|---|---|
| R1-01-03 | Reskin design (tenant DS), refined 2026-10-22 (ST-7): orange (accent) buttons now have dark text. Awaiting owner review (ST-8d). Sidebar items, badges and helper lines Stitch added are not specs: menus come from constants/SidebarMenu.ts and the menu API. |
| R1-01-03-phone | Generated 2026-10-22 by converting the existing screen. Awaiting owner review (ST-8d). Phone agent home with bottom navigation. Not specs: earning numbers, the service tiles beyond the menu API, the notification count. |
| R1-02 | Reskin design (tenant DS), refined 2026-10-22 (ST-7): the 'ELOKA / Tenant login workspace' header is gone and the label is sentence case. Awaiting owner review (ST-8d). 'Secured by <org name>' depends on FQ-8. |
| R1-02-phone | Generated 2026-10-22 by converting the existing screen. Awaiting owner review (ST-8d). Phone tenant login + OTP. |
| R1-04 | Reskin design (tenant DS), generated 2026-10-21, awaiting owner review (ST-8d). Sidebar items, badges and helper lines Stitch added are not specs: menus come from constants/SidebarMenu.ts and the menu API; orange (accent) buttons need dark text. |
| R1-04-phone | Created in Stitch by the owner from the hand-off prompts (2026-10-22) and checked against the spec. Awaiting owner review (ST-8d). |
| R1-05 | Reskin design (tenant DS), generated 2026-10-21, awaiting owner review (ST-8d). Sidebar items, badges and helper lines Stitch added are not specs: menus come from constants/SidebarMenu.ts and the menu API; orange (accent) buttons need dark text. |
| R1-05-phone | Created in Stitch by the owner from the hand-off prompts (2026-10-22) and checked against the spec. Awaiting owner review (ST-8d). Agents shown as cards. |
| R1-06 | Reskin design (tenant DS), generated 2026-10-21, awaiting owner review (ST-8d). Sidebar items, badges and helper lines Stitch added are not specs: menus come from constants/SidebarMenu.ts and the menu API; orange (accent) buttons need dark text. |
| R1-06-phone | Created in Stitch by the owner from the hand-off prompts (2026-10-22) and checked against the spec. Awaiting owner review (ST-8d). Transactions shown as cards. |
| R1-07 | Reskin design (tenant DS), generated 2026-10-21, awaiting owner review (ST-8d). Sidebar items, badges and helper lines Stitch added are not specs: menus come from constants/SidebarMenu.ts and the menu API; orange (accent) buttons need dark text. |
| R1-07-phone | Phone profile, created in Stitch by the owner (2026-10-22). Awaiting owner review (ST-8d). Not specs: the full mobile number and email (the build masks them), shop and bank details are samples. |
| R1-08-11 | Reskin design (tenant DS), generated 2026-10-21, awaiting owner review (ST-8d). Sidebar items, badges and helper lines Stitch added are not specs. |
| R1-08-11-phone | Generated 2026-10-22 by converting the existing screen. Awaiting owner review (ST-8d). Phone business settings with shared empty/error/404 states. Not spec: the settings list comes from the app's menus. |
| R1-09 | Reskin design (tenant DS), generated 2026-10-21, awaiting owner review (ST-8d). Sidebar items, badges and helper lines Stitch added are not specs. |
| R1-10 | Reskin design (tenant DS), generated 2026-10-21, awaiting owner review (ST-8d). Sidebar items, badges and helper lines Stitch added are not specs. |
| R1-10-phone | Generated 2026-10-22 by converting the existing screen. Awaiting owner review (ST-8d). Phone query center with tickets as cards. Not specs: ticket ids, names and the bottom-bar tabs (they come from the app). |
| R1-theme-check | Theme check 2026-10-22: the agent home in a second tenant brand (teal #0F766E, amber accent) to show white-label theming. Awaiting owner review (ST-8d). Not specs: 'Today's volume', commission and settlement lines, 'Terminal Online', agent id, version label; the dates (2023) are sample only. |
