#!/usr/bin/env bash
# Deploys a published version to one host over SSH (used by .github/workflows/deploy.yml).
# Backup first, then pull the images, migrate and start, then check the site answers.
#   SSH_KEY KNOWN_HOSTS HOST DEPLOY_USER DEPLOY_DIR SITE deploy/remote-deploy.sh v1.2.3
set -euo pipefail

VERSION="${1:?version tag, e.g. v1.2.3}"
[[ "$VERSION" =~ ^v[0-9]+\.[0-9]+\.[0-9]+$ ]] || { echo "not a version tag: $VERSION"; exit 1; }
: "${SSH_KEY:?}" "${KNOWN_HOSTS:?}" "${HOST:?}" "${DEPLOY_USER:?}" "${DEPLOY_DIR:?}" "${SITE:?}"

umask 077
mkdir -p ~/.ssh
printf '%s\n' "$SSH_KEY" > ~/.ssh/deploy_key
printf '%s\n' "$KNOWN_HOSTS" > ~/.ssh/known_hosts
SSH=(ssh -i ~/.ssh/deploy_key -o StrictHostKeyChecking=yes "$DEPLOY_USER@$HOST")

"${SSH[@]}" bash -s -- "$VERSION" "$DEPLOY_DIR" <<'REMOTE'
set -euo pipefail
VERSION="$1"; cd "$2"
deploy/backup.sh # with the running release's files, before anything changes
git fetch --tags --quiet && git checkout --quiet "$VERSION"
export ELOKA_VERSION="$VERSION"
docker compose -f deploy/docker-compose.yml --env-file deploy/.env pull
docker compose -f deploy/docker-compose.yml --env-file deploy/.env up -d --wait
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
