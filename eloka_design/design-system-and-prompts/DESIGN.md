---
name: ELOKA Platform
colors:
  surface: '#FAFAF7'
  surface-dim: '#E9E8E2'
  surface-bright: '#FFFFFF'
  surface-container-lowest: '#FFFFFF'
  surface-container-low: '#F4F3EE'
  surface-container: '#EFEEE8'
  surface-container-high: '#E9E8E2'
  surface-container-highest: '#E5E5E0'
  on-surface: '#0A0A0A'
  on-surface-variant: '#5C5C57'
  inverse-surface: '#0A0A0A'
  inverse-on-surface: '#FFFFFF'
  outline: '#85857F'
  outline-variant: '#E5E5E0'
  surface-tint: '#11299E'
  primary: '#0A0A0A'
  on-primary: '#FFFFFF'
  primary-container: '#1F1F1F'
  on-primary-container: '#FFFFFF'
  inverse-primary: '#C9D1F5'
  secondary: '#11299E'
  on-secondary: '#FFFFFF'
  secondary-container: '#E8ECFA'
  on-secondary-container: '#091968'
  tertiary: '#5C5C57'
  on-tertiary: '#FFFFFF'
  tertiary-container: '#F4F3EE'
  on-tertiary-container: '#0A0A0A'
  error: '#991B1B'
  on-error: '#FFFFFF'
  error-container: '#FEF2F2'
  on-error-container: '#991B1B'
  background: '#FAFAF7'
  on-background: '#0A0A0A'
  surface-variant: '#F4F3EE'
  success: '#065F46'
  success-container: '#ECFDF5'
  warning: '#92400E'
  warning-container: '#FFFBEB'
  info: '#1E40AF'
  info-container: '#EFF6FF'
typography:
  display:
    fontFamily: Inter
    fontSize: 32px
    fontWeight: '700'
    lineHeight: 40px
    letterSpacing: -0.02em
  display-mobile:
    fontFamily: Inter
    fontSize: 24px
    fontWeight: '700'
    lineHeight: 32px
    letterSpacing: -0.01em
  h1:
    fontFamily: Inter
    fontSize: 24px
    fontWeight: '600'
    lineHeight: 32px
    letterSpacing: -0.01em
  h2:
    fontFamily: Inter
    fontSize: 20px
    fontWeight: '600'
    lineHeight: 28px
  h3:
    fontFamily: Inter
    fontSize: 16px
    fontWeight: '600'
    lineHeight: 24px
  body:
    fontFamily: Inter
    fontSize: 16px
    fontWeight: '400'
    lineHeight: 24px
  body-sm:
    fontFamily: Inter
    fontSize: 14px
    fontWeight: '400'
    lineHeight: 20px
  label:
    fontFamily: Inter
    fontSize: 12px
    fontWeight: '500'
    lineHeight: 16px
    letterSpacing: 0.01em
rounded:
  sm: 6px
  DEFAULT: 10px
  md: 10px
  lg: 14px
  xl: 18px
  full: 9999px
spacing:
  base: 4px
  xs: 4px
  sm: 8px
  md: 12px
  lg: 16px
  xl: 24px
  2xl: 32px
  3xl: 48px
  4xl: 64px
---

# Design System: ELOKA Platform

**Project ID:** to be filled in after the Stitch project is created (see `metadata.json`)

ELOKA is Eko's white-label fintech platform for India. Businesses apply to run their own branded payments portal as a **Local Admin**, and Eko staff review each application. This design system covers the **platform screens** (applicant onboarding and the staff review console) and sets the base for the tenant-branded app.

## 1. Visual Theme & Atmosphere

**Quiet authority.** The interface should feel like a well-run bank branch: calm, orderly and certain. Surfaces are a warm paper white rather than clinical grey, ink is near-black, and there is exactly one decisive action on each view. Nothing decorates for its own sake. Trust comes from precision: aligned edges, consistent spacing, plain words, and status that is always visible.

Density is balanced, like a daily-use tool (5 of 10). Layouts are symmetric and predictable (variance 3 of 10), because applicants are filling in serious financial paperwork and staff are making compliance decisions. Motion is restrained (3 of 10): short 150–200 ms fades and slides for dialogs and state changes, with no looping animation. Applicant screens are mobile-first, because most applicants finish onboarding on a phone. Staff screens are desktop-first and information-dense.

## 2. Color Palette & Roles

### Primary Foundation
- **Warm Paper Canvas** (#FAFAF7): the page background everywhere.
- **Pure Surface White** (#FFFFFF): cards, inputs, dialogs and table bodies. These are the lifted layer.
- **Soft Linen** (#F4F3EE): recessed areas such as table headers, sidebars, read-only fields and code-like values.
- **Hairline Stone** (#E5E5E0): decorative 1px dividers and card outlines.

### Accent & Interactive
- **Ink Black** (#0A0A0A): the single primary call to action (solid button, white text) and the inverse surface for toasts.
- **Pressed Ink** (#1F1F1F): hover and pressed state of the primary button.
- **Connect Blue** (#11299E): links, focus rings, the selected item in navigation, progress and the "current step" marker. It is never used for large fills.
- **Connect Blue Wash** (#E8ECFA): the subtle background behind a selected row or the active step.
- **Field Edge Grey** (#85857F): the border of inputs, selects, checkboxes and radios (it meets 3:1 against white).

### Typography & Text Hierarchy
- **Ink Black** (#0A0A0A): headings and body text.
- **Graphite** (#5C5C57): labels, metadata, secondary lines.
- **Muted Graphite** (#6F6F6A): hints, placeholders and timestamps. It still passes 4.5:1 on every surface.
- **White** (#FFFFFF): text on Ink Black.

### Functional States (soft fill + dark text + matching border; never colour alone)
- **Success**: Deep Forest (#065F46) on Mint Mist (#ECFDF5), border (#A7F3D0). Used for completed, verified, approved and signed.
- **Danger**: Oxblood (#991B1B) on Blush (#FEF2F2), border (#FCA5A5). Used for failed, rejected, blocked, expired and withdrawn.
- **Warning**: Burnt Umber (#92400E) on Cream (#FFFBEB), border (#FCD34D). Used for correction required, on hold, reopened and needs manual review.
- **Info**: Royal Ink Blue (#1E40AF) on Ice (#EFF6FF), border (#BFDBFE). Used for in progress, submitted, under review and pending external checks.
- **Neutral / Locked**: Graphite (#5C5C57) on Soft Linen (#F4F3EE). Used for not started, skipped and locked (shown with a lock icon).

### Tenant brand slot (tenant app only, not on platform screens)
Tenant apps replace Ink Black actions with the tenant's own primary colour. The foreground (white or Ink Black) is chosen automatically so that it reaches 4.5:1 contrast. Generated platform screens must use the palette above.

## 3. Typography Rules

**Inter** throughout: a neutral, highly legible humanist grotesque that renders the rupee sign and Indian names well. Amounts, IDs, PAN and account fragments, and timestamps use tabular figures so columns line up.

### Hierarchy & Weights
- **Display** 32/40, bold 700 (24/32 on mobile): outcome screens ("Application submitted") and the hub greeting.
- **H1** 24/32, semibold 600: page titles.
- **H2** 20/28, semibold 600: section titles.
- **H3** 16/24, semibold 600: card titles.
- **Body** 16/24, regular 400: body text and input values (inputs are never smaller than 16px on mobile).
- **Body small** 14/20, regular 400: table cells, help text, metadata.
- **Label** 12/16, medium 500: field labels, column headers, overlines. Sentence case, never all caps.

### Spacing Principles
Headings sit tight (slight negative tracking on display and H1). Body text has relaxed leading, and lines stay within about 70 characters. Hierarchy comes from weight and size, never from colour alone.

## 4. Component Stylings

### Buttons
- **Primary**: solid Ink Black, white label, gently rounded corners (10px), 40px tall (48px full-width on mobile step forms). Hover turns Pressed Ink. Pressed nudges down 1px. There is a visible 2px Connect Blue focus ring with a 2px offset. While loading, a small inline spinner replaces the label, and the button keeps its width.
- **Secondary**: white with a 1px Field Edge Grey outline and Ink Black label.
- **Ghost**: no fill or border, Graphite label, Soft Linen on hover.
- **Danger**: Oxblood label on Blush with an Oxblood outline. It is used only inside confirmation dialogs.
- **Link**: Connect Blue text, underline on hover.
- At most one primary button per view.

### Cards & Panels
- White surface on the Warm Paper canvas, a 1px Hairline Stone outline, generously rounded corners (14px), and a whisper-soft shadow (0 1px 2px at 4% ink). Padding is 24px on desktop and 16px on mobile.
- Section cards have a header row (H3 title plus an optional status pill or action on the right), a body, and an optional footer row for actions.

### Status Pills
- Fully pill-shaped, 24px tall, a small 8px dot plus a sentence-case label in Label type, using the functional-state fills above. The text is always present.

### Navigation
- **Applicant**: a slim top bar with the ELOKA wordmark on the left and Help plus Sign out on the right. On desktop a 280px vertical progress rail lists all 13 steps, each with a status icon. On mobile this becomes a compact "Step 4 of 13" bar with a thin progress line.
- **Staff**: a 240px left sidebar ("Queue", "Assigned to me") on Soft Linen. The active item has a Connect Blue Wash background and a 3px Connect Blue left marker. The top bar shows the staff member's name and roles.

### Inputs & Forms
- The label always sits above the field. Help text is below in Body small Graphite. Errors are below in Oxblood with a small icon and replace the help text.
- Fields are white with a 1px Field Edge Grey border and 10px corners, 40px tall (48px on mobile). Focus gives a 2px Connect Blue ring. Invalid fields get an Oxblood border.
- A required marker appears after the label. Optional fields say "(optional)".
- OTP entry is 6 separate square boxes (48px), auto-advancing, with paste support.

### Domain-Specific Components
- **Document tile**: a row card with a file-type icon, document name, file name and size, a status pill, the reviewer note (if rejected) in a Blush strip, and actions on the right (View, Replace, Remove).
- **Pending check card**: an Ice background, a small progress indicator, the title "We're checking with the provider", and a "Check again" link after a while.
- **Timeline**: a vertical line with a dot per event, the time in Muted Graphite on the left (desktop) or above (mobile), and the event in Body.
- **Masked value**: Soft Linen chip with tabular figures, for example XXXXX1234X, plus a small copy or reveal icon button with a label.
- **Approval blockers panel**: a Cream warning card listing each blocker as a sentence, shown above the decision buttons.

## 5. Layout Principles

### Grid & Structure
- Applicant: a single content column with max-width 640px on mobile and tablet. On desktop (1024px+) it becomes a 280px progress rail plus a 720px form column, centred.
- Staff: fluid up to 1400px. The queue is a full-width table. The case view uses a main column with tabs or sections and a 320px right column for the decision panel and blockers.
- Breakpoints: 440, 768, 1024, 1200px.

### Whitespace Strategy
A 4px base grid. The common steps are 8, 12, 16, 24, 32 and 48. Use 32px between sections on desktop (24px on mobile), 16px between fields, and 8px between a label and its field.

### Alignment & Visual Balance
Left-aligned text everywhere, except short outcome screens, which may be centred. Primary actions are bottom-right on desktop forms, and a full-width sticky footer on mobile forms (with safe-area padding).

### Responsive Behavior & Touch
Mobile-first for applicants. Every multi-column layout collapses to one column below 768px. Touch targets are at least 44px. There is never horizontal page scroll; wide tables become stacked cards below 860px.

## 6. Design System Notes for Stitch Generation

### Language to Use
"Calm, precise, trustworthy fintech onboarding"; "warm paper background with white cards"; "single solid black primary button"; "labels above inputs, helper text below"; "status pills with a dot and text"; "plenty of whitespace, no decoration".

### Color References
Warm Paper Canvas (#FAFAF7), Pure Surface White (#FFFFFF), Soft Linen (#F4F3EE), Ink Black (#0A0A0A), Graphite (#5C5C57), Connect Blue (#11299E), Field Edge Grey (#85857F), plus the four functional pairs above.

### Component Prompts
- "A step form card titled 'Business PAN' with one labelled input, a hint line under it, and a full-width primary button 'Verify PAN' pinned to the bottom on mobile."
- "A review queue table with columns Application, Company, Entity type, Status (pill), Submitted, Assigned to, Flags, with a filter bar above and 'Load more' below."
- "A confirmation dialog 'Reject application' with a required reason select, a message to the applicant, a separate internal note, and Cancel plus a danger 'Reject' button."

### Incremental Iteration
Generate the component sheet first, then screens in flow order, always reusing this design system. Edit one thing at a time. Never introduce new colours or fonts in a screen prompt.

### Never Do (anti-patterns)
- No emojis.
- No invented metrics, statistics, testimonials or "trusted by" logos.
- No gradients, glows or neon. No purple or blue gradient hero sections.
- No pure #000000.
- No three equal feature cards in a row.
- No marketing clichés ("Elevate", "Seamless", "Unleash", "Next-gen").
- No real-looking personal data: use clearly fictional sample values (sample business "Sample Traders Pvt Ltd", PAN shown masked as XXXXX1234X).
- No dark mode.
