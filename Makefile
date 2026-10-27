# One entry point for common tasks. Run `make help`.
# The development Mac has 8 GB RAM: targets run heavy jobs one at a time with small memory.
.DEFAULT_GOAL := help
.PHONY: help setup client-dev client-check client-build server-run server-test server-build images check load-test

GRADLE := ./gradlew --no-daemon --max-workers=1 -Dorg.gradle.jvmargs=-Xmx512m

help: ## List the available commands
	@grep -E '^[a-zA-Z_-]+:.*?## ' $(MAKEFILE_LIST) | awk 'BEGIN {FS = ":.*?## "}; {printf "  \033[36m%-14s\033[0m %s\n", $$1, $$2}'

setup: ## Install client dependencies
	cd client && npm install

client-dev: ## Web app on :3003
	cd client && npm run dev

client-check: ## Client lint, type check and tests (like CI)
	cd client && npm run lint && npm run typecheck && npm run test:ci

client-build: ## Client production build
	cd client && NODE_OPTIONS=--max-old-space-size=2048 npm run build

server-run: ## Backend on :8091 (local profile)
	cd server && ./gradlew bootRun

server-test: ## Backend tests (Docker needed once database tests exist)
	cd server && $(GRADLE) test

server-build: ## Backend jar in server/build/libs
	cd server && $(GRADLE) bootJar

images: ## Build both production images (one after the other)
	docker build -t eloka-partner-server:local server
	docker build -t eloka-partner-client:local client

check: client-check server-test ## Everything CI runs, one after the other

load-test: ## k6 against a local server on :8091 (fake providers): sign-in and application start
	docker run --rm -i -e BASE_URL=http://host.docker.internal:8091 grafana/k6:1.3.0 run - < server/load/partner-journey.js
