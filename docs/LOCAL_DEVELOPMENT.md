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
| Email code | every code is `123456`; no email is sent (use any address, one per open application) |
| DigiLocker | returns at once (straight back to `/apply/identity?state=…`) with the sample person RAVI SAMPLE, born 1990-01-15, Aadhaar `XXXX-XXXX-1234`, a whole address in Mumbai |
| PAN check (by the 4 digits of the PAN) | `0000` invalid · `1111` name mismatch · `2222` date-of-birth mismatch · `9999` provider down · anything else matches |
| Credit score (by the 4 digits of the PAN) | `6990` → 699 (not eligible) · `7000` → 700 (eligible) · `0001` → no credit history · `9997` → 950 (invalid score) · `9998` → bureau down · anything else → 742 |
| Voter ID (by the last 4 digits of the EPIC, e.g. `ABC1234567`) | `0000` no record · `1111` someone else's name · `9999` provider down · anything else RAVI SAMPLE |
| Driving licence (by its last 4 digits, e.g. `MH0120110014567`) | `0000` no record · `1111` someone else's name · `2222` date of birth does not match · `3333` expired · `9999` provider down · anything else RAVI SAMPLE's active licence |
| Name match | the same letters (ignoring case, spaces, dots) match; anything else does not |
| Malware scanner | flags only the EICAR test string |

PANs must be personal (4th letter `P`), for example `ABCPK4321L`. A PAN can be used by one open, approved or
ineligible application only, so use new letters for each try. The same goes for voter ID and driving licence numbers.

## Staff console sign-in (local)

OTP `123456`: `9000000006` reviewer · `9000000007` approver · `9000000008` both roles. Seeded at start-up in the
`local` and `test` profiles only.

## Checks before a commit

```bash
make client-check    # lint, type check, Jest
make server-test     # all server tests (Docker)
```

The Mac has 8 GB RAM: run one heavy job at a time.
