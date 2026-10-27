# ADR-0003: Browser session through a Next.js BFF with an HttpOnly cookie

- **Status:** accepted
- **Date:** 2026-10-27
- **Deciders:** project owner

## Context

Applicants upload identity documents and run a credit check. In eloka the browser keeps opaque Bearer tokens in `sessionStorage`, where any injected script can read them.

## Decision

- The server keeps eloka's opaque, database-backed session tokens (256-bit random, only SHA-256 stored, idle + absolute expiry, refresh rotation).
- The browser never sees them. The Next.js route `client/src/pages/api/v1/[...path].ts` is a thin proxy (BFF): on OTP verify it stores the tokens in `HttpOnly; Secure; SameSite=Strict` cookies and adds `Authorization: Bearer` when it forwards a call to the server over the internal network.
- CSRF: `SameSite=Strict`, an `Origin` check and a required `X-Requested-With: eloka` header on every state-changing proxy call.
- The server stays stateless and Bearer-only; it can be called by tests and by the proxy alike.

## Options considered

| Option | Pros | Cons |
|---|---|---|
| BFF + HttpOnly cookie (chosen) | Tokens out of reach of scripts | One more hop, proxy must pass status codes and headers unchanged |
| Bearer in `sessionStorage` (eloka) | Simple | XSS can steal sessions |
| Server-set cookies, CORS with credentials | No proxy | CSRF and CORS complexity on the API |

## Consequences

- The proxy must forward `ETag`, `If-Match`, `Idempotency-Key`, `X-Request-Id`, multipart bodies and real status codes.
- Only the proxy needs the server's internal URL; no API host in the browser's CSP.
