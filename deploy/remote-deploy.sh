#!/usr/bin/env bash
# Deploys a published version to one host over SSH (used by .github/workflows/deploy.yml).
# Backup first, then pull the images, migrate and start, then check the site answers.
#   SSH_KEY KNOWN_HOSTS HOST DEPLOY_USER DEPLOY_DIR SITE ENV_FILE deploy/remote-deploy.sh v1.2.3
# ENV_FILE is the settings file on the host, inside deploy/ (.env.staging, .env.production; default .env).
set -euo pipefail

VERSION="${1:?version tag, e.g. v1.2.3}"
[[ "$VERSION" =~ ^v[0-9]+\.[0-9]+\.[0-9]+$ ]] || { echo "not a version tag: $VERSION"; exit 1; }
: "${SSH_KEY:?}" "${KNOWN_HOSTS:?}" "${HOST:?}" "${DEPLOY_USER:?}" "${DEPLOY_DIR:?}" "${SITE:?}"
ENV_FILE="${ENV_FILE:-.env}"
[[ "$ENV_FILE" =~ ^\.env(\.[a-z]+)?$ ]] || { echo "not a settings file name: $ENV_FILE"; exit 1; }

umask 077
mkdir -p ~/.ssh
printf '%s\n' "$SSH_KEY" > ~/.ssh/deploy_key
printf '%s\n' "$KNOWN_HOSTS" > ~/.ssh/known_hosts
SSH=(ssh -i ~/.ssh/deploy_key -o StrictHostKeyChecking=yes "$DEPLOY_USER@$HOST")

"${SSH[@]}" bash -s -- "$VERSION" "$DEPLOY_DIR" "$ENV_FILE" <<'REMOTE'
set -euo pipefail
VERSION="$1"; cd "$2"
export ELOKA_ENV_FILE="$3" # read by backup.sh and by the compose services
deploy/backup.sh # with the running release's files, before anything changes
git fetch --tags --quiet && git checkout --quiet "$VERSION"
export ELOKA_VERSION="$VERSION"
docker compose -f deploy/docker-compose.yml --env-file "deploy/$ELOKA_ENV_FILE" pull
docker compose -f deploy/docker-compose.yml --env-file "deploy/$ELOKA_ENV_FILE" up -d --wait
REMOTE

for i in $(seq 1 30); do
	if curl -fsS "$SITE/api/health" > /dev/null; then
		echo "deployed $VERSION to $SITE"
		exit 0
	fi
	sleep 5
done
echo "$SITE did not answer after deploying $VERSION; see RUNBOOK 'Roll back'"
exit 1
