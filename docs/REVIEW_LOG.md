# CodeRabbit review log

CodeRabbit CLI (`coderabbit review --agent --uncommitted`) runs once per cohesive task, after lint and tests are green. The included allowance is 3 reviews per rolling hour (`coderabbit usage`). Critical and major findings are fixed before the commit.

| Date | Task | Severity | File | Finding | Outcome |
|---|---|---|---|---|---|
| 2026-10-27 | P2 bootstrap | major | `client/Dockerfile` | runtime `COPY public` fails if `client/public/` is missing | no change: `client/public/.gitkeep` is part of the same commit |
