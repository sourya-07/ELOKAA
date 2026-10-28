# Runbook: staging and production

One host per environment, running `deploy/docker-compose.yml`: MySQL, a one-shot migration job,
the server, the web app, ClamAV and Caddy (TLS). Only Caddy publishes ports (80, 443). Staging and
production are the same images with different settings files, `deploy/.env.staging` and
`deploy/.env.production` (different keys, databases,
provider accounts and staff OAuth clients; never share them).

> Not yet rehearsed on a real host. Before the first production go-live: one full staging run of
> this page, including a backup and a restore.

## Host requirements

- Linux, Docker Engine with the compose plugin, 4 GB RAM or more (ClamAV alone needs about 1.5 GB).
- DNS: `ELOKA_DOMAIN` points at the host; ports 80 and 443 open (Let's Encrypt).
- The repository checked out in `DEPLOY_DIR`; `deploy/.env.<environment>` filled in (from `deploy/.env.example`).
  Each file sets `ELOKA_ENV_FILE` to its own name, so the services read the same file. Never commit them
  (`.gitignore` covers `deploy/.env.*`); keep a copy in the secrets manager.
- Off-host storage for backups.

## First installation

```bash
E=staging                               # or production
cp deploy/.env.example deploy/.env.$E   # fill every value; openssl rand -base64 32 for each key
docker compose -f deploy/docker-compose.yml --env-file deploy/.env.$E up -d --wait
curl -fsS https://<your ELOKA_DOMAIN>/api/health
```

The server refuses to start if `ELOKA_ENVIRONMENT` is `staging`/`production` and anything unsafe is
configured (a fake provider, a published key, staff OTP sign-in). The log names what to fix.

## First staff account

Staff sign in with Google (accounts of `ELOKA_STAFF_GOOGLE_DOMAIN` only). Create each account once:

```bash
docker compose -f deploy/docker-compose.yml --env-file deploy/.env.$E --profile tools run --rm \
  -e ELOKA_STAFF_EMAIL=name@eko.co.in -e ELOKA_STAFF_NAME="Name" -e ELOKA_STAFF_ROLES=REVIEWER,APPROVER \
  staff-bootstrap
```

`ELOKA_STAFF_ACTIVE=false` with the same email disables an account. Approval needs two different
people (one recommends, another approves), so create at least one reviewer and one approver.

## Deploy

Automatic: push a tag `vX.Y.Z` on `main`. `.github/workflows/deploy.yml` builds, scans (Trivy) and
publishes the images, deploys to staging, checks `/api/health`, then waits for an approval on the
`production` environment. It needs, per GitHub environment (`staging`, `production`): variables
`DEPLOY_HOST`, `DEPLOY_USER`, `DEPLOY_DIR`, `SITE_URL`; secrets `DEPLOY_SSH_KEY`, `DEPLOY_KNOWN_HOSTS`.
The staging job uses `deploy/.env.staging` on its host, the production job `deploy/.env.production`.

By hand on the host:

```bash
git fetch --tags && git checkout vX.Y.Z
ELOKA_ENV_FILE=.env.$E deploy/backup.sh
ELOKA_VERSION=vX.Y.Z docker compose -f deploy/docker-compose.yml --env-file deploy/.env.$E pull
ELOKA_VERSION=vX.Y.Z docker compose -f deploy/docker-compose.yml --env-file deploy/.env.$E up -d --wait
```

Migrations run in the `migrate` job before the server starts. They only add (expand); columns or
tables are removed in a later release, after no running version uses them (contract).

## Test stack on one machine

The production images with fake providers, to try the whole stack (Caddy, ClamAV, migrate job) before
staging. Never for real data. `deploy/.env.test` sets `ELOKA_ENVIRONMENT=test`, fake providers (OTP
`123456`), a self-signed certificate for `localhost` and the seeded staff logins (9000000006 reviewer,
…07 approver, …08 both; OTP sign-in).

```bash
make images
docker compose -f deploy/docker-compose.yml --env-file deploy/.env.test up -d --wait   # https://localhost
docker compose -f deploy/docker-compose.yml --env-file deploy/.env.test down           # -v also wipes the data
```

Needs about 4 GB of free memory (ClamAV starts too).

## Roll back

```bash
ELOKA_VERSION=v<previous> docker compose -f deploy/docker-compose.yml --env-file deploy/.env.$E up -d --wait
```

The schema is not rolled back: the previous version still works on it because migrations only
add. Restore from a backup only after data loss (below).

## Backups

`deploy/backup.sh` writes a consistent MySQL dump and the encrypted uploads, with checksums, and
keeps 14 days locally. Run it daily (cron) with the host's settings file, for example
`ELOKA_ENV_FILE=.env.production deploy/backup.sh`, and copy the folder off the host. The uploads and
encrypted columns need `ELOKA_CRYPTO_FIELD_KEY`/`ELOKA_CRYPTO_INDEX_KEY`: keep those in the secrets
manager, never next to the backups.

`ELOKA_ENV_FILE=.env.<environment> deploy/restore.sh <backup-dir> <STAMP>` replaces the database and the uploads (it asks first).

## Logs and health

- `docker compose … logs -f server`: JSON (ECS) lines with `request_id`, personal data masked.
- Audit trail: the `audit_event` table (hash-chained); every review decision and reveal is there.
- Health: `https://<ELOKA_DOMAIN>/api/health` (web app); the server's readiness is checked by Docker.

## Known gaps before production

- No credit bureau is contracted (docs/OPEN_QUESTIONS.md Q-1): with `ELOKA_CREDIT_BUREAU=none`
  applicants stop at the credit check with "not available yet".
- EPS adapters (OTP, PAN, DigiLocker) are tested against a mock server only; run the journey on
  EPS UAT in staging first.
- Retention periods (Q-5) are not decided; nothing is deleted automatically yet.
