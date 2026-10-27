# CLAUDE.md — server/

Java 25, Spring Boot 4.1, Spring Modulith 2.1, Spring Security, MySQL 8.4, Flyway, Gradle (Kotlin DSL). Read the root [`CLAUDE.md`](../CLAUDE.md) first.

## Commands

```bash
./gradlew bootRun     # :8091, profile `local`
./gradlew --no-daemon --max-workers=1 test   # all tests, including ModularityTests
./gradlew bootJar     # build/libs/eloka-partner-server-<version>.jar
docker build -t eloka-partner-server:<version> .
```

## Layout

```
src/main/java/in/eko/eloka/partner/
└── ElokaPartnerApplication.java   modules are sub-packages of this package (planned: shared, audit,
                                   identity, integration, documents, verification, eligibility,
                                   partneronboarding, review, system)
src/main/resources/
└── application.yaml / application-local.yaml / application-prod.yaml
```

## Rules

- **Modules** talk only through another module's top-level package (its API) or events; `internal` sub-packages are private. `ModularityTests` enforces it.
- **Entities guard their rules:** no public setters; intention-revealing methods; constructors set required state.
- Controllers map HTTP ↔ DTO records only; business errors are `DomainException` subclasses (problem+json).
- Every schema change is a new Flyway migration; never edit one that has run.
