# Local development

## Run it

```bash
open -a Docker                       # Testcontainers and the local MySQL need Docker
export JAVA_HOME=/opt/homebrew/opt/openjdk
make server-run                      # :8091, MySQL from server/compose.yaml on 127.0.0.1:3308
make setup && make client-dev        # :3003
```

Then open <http://localhost:3003/apply> (applicant, OTP `123456` for any mobile) or
<http://localhost:3003/staff> (staff, see below). The client calls the server only through its proxy
(`/api/v1/**`); settings are in `client/.env.development`.

## Fake providers (`local` and `test` profiles only)

Every outside service runs on a fake locally (ADR-0004). Staging and production refuse to start with any of them.

| Service | How to get each answer |
|---|---|
| Mobile OTP | every code is `123456`; no SMS is sent |
| DigiLocker | returns at once with the sample person RAVI SAMPLE, Aadhaar `XXXX-XXXX-1234`, an address in Mumbai |
| PAN check (by the 4 digits of the PAN) | `0000` invalid · `1111` name mismatch · `2222` date-of-birth mismatch · `9999` provider down · anything else matches |
| Credit score (by the 4 digits of the PAN) | `6990` → 699 (not eligible) · `7000` → 700 (eligible) · `0001` → no credit history · `9997` → 950 (invalid score) · `9998` → bureau down · anything else → 742 |
| Malware scanner | flags only the EICAR test string |

PANs must be personal (4th letter `P`), for example `ABCPK4321L`. A PAN can be used by one open, approved or
ineligible application only, so use new letters for each try.

## Staff console sign-in (local)

OTP `123456`: `9000000006` reviewer · `9000000007` approver · `9000000008` both roles. Seeded at start-up in the
`local` and `test` profiles only.

## Checks before a commit

```bash
make client-check    # lint, type check, Jest
make server-test     # all server tests (Docker)
```

The Mac has 8 GB RAM: run one heavy job at a time.
