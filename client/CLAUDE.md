# CLAUDE.md — client/

Next.js 15 (Pages Router), React 18, TypeScript (strict), Chakra UI 2, Jest. Read the root [`CLAUDE.md`](../CLAUDE.md) first. React 18 is why this is the Pages Router (ADR-0002).

## Commands

```bash
npm run dev        # :3003
npm run lint       # ESLint (next/core-web-vitals + next/typescript), no warnings allowed
npm run typecheck
npm run test:ci    # Jest, 2 workers
npm run build
```

## Layout

```
src/pages/            routes only: index, apply/* (journey), staff/* (review console), api/health, api/v1/[...path] (BFF)
src/middleware.ts     per-request CSP nonce (pages render per request so the nonce is real)
src/lib/              api.ts (fetch through the proxy, ApiError), bff.ts (proxy rules, unit-tested), steps.ts
                      (pages per path, routeFor), types.ts, validation.ts, consent.ts, states.ts
src/features/apply/   ApplicationContext (snapshot + write with If-Match; 412 shows the latest), guards, PanForm,
                      SubmitStep, useDocuments, useAction, withApplication, WithdrawPanel (step-up code, then withdraw)
src/features/staff/   StaffShell (who is signed in), types
src/components/       ApplyShell/Section (journey steps link to /apply/status), OtpInput, FileSlot, CameraCapture, StatusPill, ErrorBanner
src/styles/theme.ts   Chakra theme from eloka_design/design-system-and-prompts/DESIGN.md
__tests__/            Jest tests, mirroring src/ (render.tsx wraps the theme, fixtures.ts builds snapshots)
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
- The server decides what is next: pages follow `snapshot.next_step` (`routeFor`) and guard themselves with `useStepGuard`.
- Every application write sends `If-Match` (done by `write()`); billable or once-only calls also send an `Idempotency-Key`.
- Imports use `@/` for `src/`.
- No `console.log` (lint error): personal data must never reach the browser console.
- No tokens or personal data in `localStorage`/`sessionStorage`; the session lives in an HttpOnly cookie (ADR-0003).
- No analytics or third-party scripts on onboarding pages.
