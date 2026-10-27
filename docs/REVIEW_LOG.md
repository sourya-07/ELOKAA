# CodeRabbit review log

CodeRabbit CLI (`coderabbit review --agent --uncommitted`) runs once per cohesive task, after lint and tests are green. The included allowance is 3 reviews per rolling hour (`coderabbit usage`). Critical and major findings are fixed before the commit.

| Date | Task | Severity | File | Finding | Outcome |
|---|---|---|---|---|---|
| 2026-10-27 | P2 bootstrap | major | `client/Dockerfile` | runtime `COPY public` fails if `client/public/` is missing | no change: `client/public/.gitkeep` is part of the same commit |
| 2026-10-27 | P3 server foundation | major | `audit/internal/DatabaseAuditLog.java` | hash used nanosecond time but `DATETIME(6)` stores microseconds, so the chain could not be re-verified | fixed: time truncated to microseconds; test re-verifies the chain from stored rows. **The eloka repo has the same bug.** |
| 2026-10-27 | P3 server foundation | minor | `audit/internal/DatabaseAuditLog.java` | `|`-joined canonical form is ambiguous | fixed: length-prefixed fields |
| 2026-10-27 | P3 server foundation | minor | `shared/crypto/CryptoConfig.java` | published test keys not refused; string compare misses padding/whitespace variants | fixed: decoded-byte compare, test keys listed, test added |
| 2026-10-27 | P3 server foundation | minor | `shared/crypto/Mobiles.java` | leading trunk `0` rejected | fixed: `0XXXXXXXXXX` accepted |
| 2026-10-27 | P3 server foundation | minor | `server/compose.yaml` | local MySQL published on all interfaces | fixed: bound to 127.0.0.1 |
