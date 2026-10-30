# CLAUDE.md — client/

Next.js 15 (Pages Router), React 18, TypeScript (strict), Chakra UI 2, Jest. Read the root [`CLAUDE.md`](../CLAUDE.md) first. React 18 is why this is the Pages Router (ADR-0002).

## Commands

```bash
npm run dev        # :3003
npm run lint       # ESLint (next/core-web-vitals + next/typescript), no warnings allowed
npm run typecheck
npm run test:ci    # Jest, 2 workers
npm run build
npm run e2e        # Playwright end to end (server and dev server running; docs/TESTING.md)
# predev/prebuild copy MediaPipe's WebAssembly into public/mediapipe/wasm (scripts/copy-mediapipe.mjs, not committed)
```

## Layout

```
src/pages/            routes only: index, apply/* (journey: index = contact details, identity = Aadhaar via DigiLocker,
                      address, PAN, credit check and voter ID / licence as tasks, photos, documents = identity
                      documents and education, review, status; old pages redirect in next.config.js), staff/*, api/health, api/v1/[...path] (BFF)
src/middleware.ts     per-request CSP nonce (pages render per request so the nonce is real)
src/lib/              api.ts (fetch through the proxy, ApiError, messageOf, uploadWithProgress (XHR, progress, cancel)), errors.ts (the words
                      for every server error code, docs/ERROR_CATALOG.md; a test keeps them in step), documents.ts (kind labels), bff.ts (proxy rules, unit-tested), format.ts, steps.ts
                      (PAGES: step → page#anchor, routeFor, safeNext for ?next= after sign-in), tasks.ts (step status →
                      task state), blockers.ts (submit blockers → sentence + fix link), milestones.ts (progress = finished parts, from server step
                      statuses, never a screen count), types.ts, validation.ts, consent.ts, states.ts
src/features/apply/   ApplicationContext (snapshot + write with If-Match; 412 shows the latest; loads once per
                      session, never goes back to an older snapshot), guards, PanForm, SubmitStep, useDocuments,
                      useAction, useCountdown (resend waits from the server's resend_after / retry_after only),
                      withApplication / withOptionalApplication (marks a journey page; _app wraps marked
                      pages in ONE shared provider; "optional" pages such as /apply also render signed out),
                      EmailStage (email code + data-use consent on the /apply card), DigiLockerTask (explainer, consent,
                      a direct link to DigiLocker's sign-up page in a new tab, return with ?state=, automatic re-checks, start again), AddressForm,
                      PanForm (PAN only), EligibilityTask, AlternateIdTask (voter ID or driving licence number),
                      useGeolocation (live-photo location: starts on a button press, best recent fix, never
                      invented), WithdrawPanel (step-up code,
                      then withdraw)
src/features/staff/   StaffShell (who is signed in), types
src/components/       ApplyShell/Section (milestone progress, "My application", sign-out, focus to the h1 after
                      navigation), TaskSection (one task of a multi-task page: open when current, folded when done,
                      folded again after a save; "Change" only where lib/tasks.ts canChange allows: never on a verified result),
                      OtpInput, FileSlot (progress with Cancel while sending, "Checking file…", "Try again" after a lost connection,
                      preview, remove asks first, no more files past the server's max_count; the rule shown is the server's
                      from GET /v1/partner/document-rules via useDocuments().ruleFor, PHOTO/PAPER are the fallback copy; lib/validation.ts
                      checkFile reads the bytes like the server: real type, min/max size, complete file), CameraCapture (named camera errors, mirrored 4:5 view with a face
                      guide, ≤1600 px, a blink first (components/useBlink.ts: MediaPipe face landmarks, self-hosted in
                      public/mediapipe; a timed eyes-closed picture where it cannot run), checked phone-camera fallback with an
                      eyes-closed and an eyes-open picture), StatusPill and StatusIcon (every status is a mark and words,
                      never colour alone), ErrorBanner
src/i18n/             locales.ts (the seven languages in the owner's order, named in their own script), en-GB.ts (the source
                      catalog), catalogs.ts (a language is usable only when 100 % of keys are there and a person reviewed it;
                      today only English (UK)), I18nProvider (useI18n().t, cookie eloka_lang, <html lang/dir>); moved so far:
                      ApplyShell, the start page, the status page (the other pages, form messages and lib/errors.ts are next)
src/components/LanguageSelector.tsx  always the seven; ones without a reviewed translation are listed as "not available yet"
src/styles/font.ts    Inter, self-hosted with next/font/local (fonts/, OFL); no font requests to third parties
src/styles/theme.ts   Chakra theme from eloka_design/design-system-and-prompts/DESIGN.md
src/styles/motion.ts  the only motion: easing curves, enter/stagger, delayed loading, reduced-motion helpers
__tests__/            Jest tests, mirroring src/ (render.tsx wraps the theme, fixtures.ts builds snapshots)
e2e/                  Playwright: journey (phone width) and layout (3 widths) with axe; helpers make test data and JPEGs
```

## Settings

| Variable | Where | Meaning |
|---|---|---|
| `ELOKA_SERVER_URL` | server-side | where the proxy reaches the server (default `http://localhost:8091`) |
| `ELOKA_STAFF_GOOGLE_CLIENT_ID` | server-side, read per request | shows the Google sign-in button on `/staff` |
| `ELOKA_STAFF_OTP_SIGN_IN` | server-side, read per request | shows the local staff OTP sign-in (`.env.development` sets it) |

No `NEXT_PUBLIC_*` settings: they would be compiled into the image, and one image serves every environment.

## Rules

- The browser calls only `/api/v1/**`; the proxy adds the session from HttpOnly cookies and checks `X-Requested-With: eloka` and the Origin on writes. Use `call()` from `@/lib/api`, never `fetch` to the server.
- While a check or a code is being sent (`useAction().busy`), its fields are disabled: nothing can be edited during a
  verification. Verified results have no "Change" at all (lib/tasks.ts).
- The server decides what is next: pages follow `snapshot.next_step` (`routeFor`) and guard themselves with `useStepGuard`.
- Every application write sends `If-Match` (done by `write()`); billable or once-only calls also send an `Idempotency-Key`.
- Imports use `@/` for `src/`.
- No `console.log` (lint error): personal data must never reach the browser console.
- No tokens or personal data in `localStorage`/`sessionStorage`; the session lives in an HttpOnly cookie (ADR-0003).
- No analytics or third-party scripts on onboarding pages.
- Applicant-facing text goes through `useI18n().t` with a key in `src/i18n/en-GB.ts` on pages already moved (a test
  fails on hard-coded text there). Never add a language without a person's reviewed translation of every key; Urdu
  also needs a right-to-left layout check, and each script its own self-hosted font (the CSP allows only 'self').
- The progress line (ApplyShell `Milestones`) animates with `framer-motion` (the v11 Chakra brings; never add the
  `motion` package), using the curves in `@/styles/motion` (`EASE_OUT_CURVE`, `EASE_IN_OUT_CURVE`): newly finished parts
  fill one after another, a new current part fills in and its name is underlined, "Now: …" and the count slide in when they
  differ from the previous page; nothing replays on a reload or while the same part is current (the run stays under about
  0.5 s); `MotionConfig reducedMotion="user"` in `_app` keeps only the fades.
- Other motion comes from `@/styles/motion` only: `transform`/`opacity`, strong ease-out curves, at most 300 ms
  (the success tick is the one exception), and a fade-only version under `prefers-reduced-motion`. What
  moves: journey pages enter in order, a finished part of the progress line fills in, task panels open and fold
  (Chakra Collapse; instant under reduced motion) and an opened panel's content rises 8 px, journey buttons give
  0.97 on press, errors, uploaded files, a changed status pill and a camera preview fade in, a status or task mark
  that turns to verified or done grows in from 0.92 (`useTurnedOn`: never on first render; fade only under reduced
  motion), loading spinners appear only after 400 ms, and a tick draws
  on "submitted"/"approved". Scrolling: what is below the screen waits hidden and rises in each time it is
  scrolled to from above (after scrolling back up past it, it waits again; parts coming in from the top stay
  still) (`useRevealOnScroll` + `staggerChildren()` or `revealChildren`; IntersectionObservers, nothing
  runs while scrolling). ApplyShell and the start page have it; give it to any new long page or list. The staff console does not animate (used all day). No looping motion on forms.
