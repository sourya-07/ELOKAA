# SITE.md — ELOKA Platform (Stitch project constitution)

## 1. Core Identity
- **Project Name:** ELOKA Platform
- **Stitch Project ID:** 18017913531452476583 (design system asset `a88c4be87ed045d89dcdc3d07e9f73d1`)
- **Mission:** Let Indian businesses apply to run their own white-label payments portal (as a Local Admin), and let Eko staff review and approve those applications safely.
- **Target Audience:**
  - Business owners and signatories applying on a phone.
  - Eko reviewers, approvers and auditors working on desktop.
  - Later: tenant admins and agents in the existing branded app.
- **Voice:** Calm, precise, plain-spoken. Short sentences. No marketing language.

## 2. Visual Language
- **Primary vibe:** quiet authority
- **Secondary vibe:** warm paper and ink
- **Tertiary vibe:** compliance-grade clarity

Source of truth for tokens: `client/.stitch/DESIGN.md` and `docs/frontend/UI_UX_PLAN.md` §2.

## 3. Architecture & File Structure

The real app is Next.js + Chakra UI. Stitch output is a **visual spec only** and is hand-ported to Chakra. Stitch HTML is never copied into the app.

- `client/.stitch/prompts/<ID>.md` — one prompt per screen (layout, content and states only; no colours or fonts)
- `client/.stitch/designs/<ID>.{html,png}` — downloaded results; approved ones are committed
- `client/.stitch/metadata.json` — project, design system and screen ids
- **Code targets:**
  - `client/features/la-onboarding` + `pages/partner/onboarding/*` (applicant)
  - `client/features/staff-console` + `pages/staff/*` (staff)
- **Navigation strategy:**
  - Applicant: hub-and-spoke (hub → step → back to hub), with a progress rail on desktop and a "Step N of 13" bar on mobile.
  - Staff: left sidebar (Queue, Assigned to me) → case view.

## 4. Live Sitemap
- [ ] DS-00 Component sheet
- [ ] APP-01 Entry + choose path (page 1) · [ ] APP-02 Mobile + OTP (page 2, both paths) · [ ] APP-03 Hub
- [ ] APP-04 Consent · [ ] APP-05 Program · [ ] APP-06 Business PAN · [ ] APP-07 Business details
- [ ] APP-08 Email · [ ] APP-09 Signatory KYC · [ ] APP-10 Live photo · [ ] APP-11 Documents
- [ ] APP-12 Bank account · [ ] APP-13 White-label setup · [ ] APP-14 Review + declaration · [ ] APP-15 Agreement + e-sign
- [ ] APP-16 Submitted / under review · [ ] APP-17 Corrections · [ ] APP-18 Outcomes
- [ ] APP-19 Re-auth dialog · [ ] APP-20 Withdraw dialog · [ ] APP-21 System states
- [ ] APP-22 DigiLocker quick path (6 steps)
- [ ] STF-01 Sign-in · [ ] STF-02 Queue · [ ] STF-03 Case view · [ ] STF-04 Document review
- [ ] STF-05 Verification decision · [ ] STF-06 Corrections dialog · [ ] STF-07 Recommendation
- [ ] STF-08 Approve / reject / hold · [ ] STF-09 PII reveal · [ ] STF-10 Audit trail · [ ] STF-11 Permission states
- [ ] R1-01 … R1-11 Reskin groups (later, one at a time)

## 5. The Roadmap (Backlog)

**High priority**
- DS-00, then owner review (gate ST-8a).
- Applicant flow APP-02 → APP-21, generated MOBILE. Also DESKTOP for APP-02, 03, 07, 11, 13 and 15.

**Medium priority**
- Staff console STF-01 → STF-11, generated DESKTOP.

**Low priority**
- Reskin groups R1-01 … R1-11, each after its own approval.

## 6. Creative Freedom Guidelines

Stay strictly inside the product requirements in `docs/frontend/UI_UX_PLAN.md`:
- Never invent screens, steps, fields, metrics or features that have no backend endpoint. Provisioning, go-live and custom domains are **not** built.
- Use fictional sample data only, for example "Sample Traders Pvt Ltd" and PAN shown masked as XXXXX1234X.
- Every screen must show its loading, empty, error, pending and success treatment, either inline or as a variant.
- Never ask for the same thing twice (owner, 2026-10-22): the mobile comes from sign-in, the business name from the PAN, the bank from the IFSC, the home address from DigiLocker on that path; "Same as home address" for the business address.
- No time estimates ("minutes") anywhere (owner, 2026-10-22).
