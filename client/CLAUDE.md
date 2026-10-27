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
src/pages/        routes only (thin); api/ holds server-side routes (health, and the BFF proxy, ADR-0003)
__tests__/        Jest tests, mirroring src/
```

## Rules

- Imports use `@/` for `src/`.
- No `console.log` (lint error): personal data must never reach the browser console.
- No tokens or personal data in `localStorage`/`sessionStorage`; the session lives in an HttpOnly cookie (ADR-0003).
- No analytics or third-party scripts on onboarding pages.
