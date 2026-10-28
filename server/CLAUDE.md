# CLAUDE.md — server/

Java 25, Spring Boot 4.1, Spring Modulith 2.1, Spring Security, MySQL 8.4, Flyway, Gradle (Kotlin DSL). Read the root [`CLAUDE.md`](../CLAUDE.md) first.

## Commands

```bash
./gradlew bootRun     # :8091, profile `local`: starts MySQL from compose.yaml (port 3308). Docker must run.
./gradlew --no-daemon --max-workers=1 test   # all tests; database tests use Testcontainers (Docker must run)
./gradlew bootJar     # build/libs/eloka-partner-server-<version>.jar
docker build -t eloka-partner-server:<version> .
```

## Layout

```
src/main/java/in/eko/eloka/partner/
├── shared/   OPEN module (ported from eloka): errors (DomainException family, ApiProblem), web
│             (ApiExceptionHandler → problem+json, generic 500 without internals), security (SecurityConfig
│             default-deny, BearerTokenFilter, Caller, CallerResolver port, SessionKind, Role), crypto
│             (FieldCipher AES-256-GCM, BlindIndex HMAC, Tokens, Masking, Mobiles), logging (RequestIdFilter,
│             PiiMasker, masked text/JSON output), config (ProductionSafetyGuard, UnsafeInProduction,
│             SafetyCheck, ElokaProperties), persistence (BaseEntity UUIDv7, PublicIds), time (Clock bean)
├── audit/    AuditLog (hash-chained audit_event, same transaction as the change), AuditTrail (read side)
├── integration/  provider ports (MobileOtpGateway); internal: ProviderConfig picks fake | eps | none per
│             `eloka.integration.*`, EpsClient (signed calls, allow-listed hosts), EPS adapters (OTP, PAN Lite,
│             DigiLocker), fakes, stubs, ClamAV scanner, SMTP email sender; ports MobileOtpGateway, PanVerifier,
│             DigiLockerProvider, CreditBureau, MalwareScanner, EmailSender, VoterIdVerifier, DrivingLicenceVerifier, NameMatcher
│             (EPS adapters in EpsIdDocumentVerifiers)
└── identity/ applicants (one per mobile), OTP challenges (provider ref + our limits), sessions
              (SessionService = CallerResolver), /v1/partner/otp, /otp/verify, /session/refresh, DELETE /session,
              /reauth (step-up code before withdrawing, `ApplicantReauth`), email codes (`EmailVerification`: our own
              code, keyed hash only, mobile-OTP limits per address/application/IP; fixed code via
              `eloka.identity.email-fixed-code` in local/test only)
├── onboarding/ partner applications: PartnerStep (prerequisites, invalidation), StepStatus, ApplicationStatus;
│             internal: ApplicationStateMachine (the only status changer), ApplicationService.write (row lock +
│             If-Match + editable check), StepContext (complete with a fingerprint: same data = no reset of
│             later steps), DocumentRequirements (the one file checklist), consent records (versions per
│             purpose), Idempotency, ExpiryJob,
│             /v1/partner/applications
├── documents/  DocumentApi: per-kind rules (DocumentKind), type by bytes, JPEG re-encode turned upright first (ImageSanitizer, ExifOrientation), PDF
│             malware scan, AES-GCM encrypted storage behind DocumentStorage (filesystem now)
├── verification/ PAN checks (against the DigiLocker identity), voter ID / driving licence checks (AlternateIdChecks:
│             record, active, DOB, name match), DigiLocker sessions (single-use state, 15 min, a new
│             start ends the open one, 10 per hour, outages are not results, under 18 refused); EvidenceStatus;
│             encrypted values + masked copies + PAN blind index
├── eligibility/ EligibilityPolicy (MIN_SCORE = 700, a constant), credit assessments (score only, never the report)
   (onboarding/internal also has IdentitySteps, EligibilityStep, DocumentSteps, StepController, and staff review:
    ReviewService (claim, evidence decisions, corrections, maker-checker approve, reject, hold, PII reveal),
    StaffReviewController /v1/admin/applications; review lives here because it changes application and step state)
   identity/ also holds staff accounts (StaffUser, roles REVIEWER/APPROVER), StaffAuthController (Google; OTP
    local/test only), LocalStaffSeed (local/test)
└── system/   MigrateOnlyRunner (profile `migrate`), StaffBootstrapRunner (profile `staff-bootstrap`)
src/main/resources/
├── application.yaml / application-local.yaml / application-prod.yaml (staging + production)
├── logback-spring.xml          prod: ECS JSON on stdout; elsewhere text console + logs/eloka-partner.log; all masked
└── db/migration/V<n>__*.sql    Flyway, one logical change per file, never edit one that has run
src/test/resources/application-test.yaml
```

## Rules

- **Modules** talk only through another module's top-level package (its API) or events; `internal` sub-packages are private. `ModularityTests` enforces it.
- **Entities guard their rules:** no public setters; intention-revealing methods; constructors set required state.
- Controllers map HTTP ↔ DTO records only; business errors are `DomainException` subclasses (problem+json).
- Every schema change is a new Flyway migration; never edit one that has run.
- **Environments:** `ELOKA_ENVIRONMENT` is `local`, `test`, `staging` or `production`. `ProductionSafetyGuard` refuses to start staging or production while any `UnsafeInProduction` bean (fake provider, fixed OTP) or failed `SafetyCheck` (published development key) is present.
- **Audit:** every business decision calls `AuditLog.record` inside the same transaction. `details` holds ids and codes only.
- **Logs:** never log request bodies; `PiiMasker` is the last line of defence, not the first.
- **Providers:** add a port in `integration/`, an adapter + fake + stub in `integration/internal/`, and one `switch` arm in `ProviderConfig`. A provider failure is `ProviderUnavailableException` (503), a missing provider `ProviderNotConfiguredException` (409); neither may ever count as a pass.
