-- Partner applications, their steps, the applicant timeline and idempotency records.
-- "Unique while it blocks" rules use stored generated columns that are NULL once the row no
-- longer blocks (MySQL allows many NULLs in a unique index). Keep the status lists in step with
-- ApplicationStatus.isTerminal() and blocksNewApplication().
CREATE TABLE partner_application
(
    id                         BINARY(16)  NOT NULL,
    public_id                  VARCHAR(32) NOT NULL,
    applicant_id               BINARY(16)  NOT NULL,
    status                     VARCHAR(30) NOT NULL,
    method                     VARCHAR(20) NULL,
    pan_hash                   BINARY(32)  NULL,
    assigned_staff_id          BINARY(16)  NULL,
    correction_due_at          DATETIME(6) NULL,
    last_applicant_activity_at DATETIME(6) NOT NULL,
    expires_at                 DATETIME(6) NOT NULL,
    status_changed_at          DATETIME(6) NOT NULL,
    submitted_at               DATETIME(6) NULL,
    closed_at                  DATETIME(6) NULL,
    version                    BIGINT      NOT NULL,
    created_at                 DATETIME(6) NOT NULL,
    updated_at                 DATETIME(6) NOT NULL,
    -- one open application per applicant
    open_applicant_key         BINARY(16) AS (CASE
        WHEN status IN ('INELIGIBLE', 'APPROVED', 'REJECTED', 'WITHDRAWN', 'EXPIRED') THEN NULL
        ELSE applicant_id END) STORED,
    -- a PAN may be held by only one open, approved or ineligible application
    blocking_pan_key           BINARY(32) AS (CASE
        WHEN status IN ('REJECTED', 'WITHDRAWN', 'EXPIRED') THEN NULL
        ELSE pan_hash END) STORED,
    PRIMARY KEY (id),
    CONSTRAINT partner_application_public_uk UNIQUE (public_id),
    CONSTRAINT partner_application_open_applicant_uk UNIQUE (open_applicant_key),
    CONSTRAINT partner_application_blocking_pan_uk UNIQUE (blocking_pan_key),
    CONSTRAINT partner_application_applicant_fk FOREIGN KEY (applicant_id) REFERENCES applicant (id),
    INDEX partner_application_applicant_idx (applicant_id, created_at),
    INDEX partner_application_status_idx (status, last_applicant_activity_at),
    CONSTRAINT partner_application_status_chk CHECK (status IN ('IN_PROGRESS', 'INELIGIBLE', 'SUBMITTED',
        'UNDER_REVIEW', 'CORRECTION_REQUIRED', 'ON_HOLD', 'APPROVED', 'REJECTED', 'WITHDRAWN', 'EXPIRED')),
    CONSTRAINT partner_application_method_chk CHECK (method IS NULL OR method IN ('DIGILOCKER', 'MANUAL'))
);

CREATE TABLE application_step
(
    id                 BINARY(16)    NOT NULL,
    application_id     BINARY(16)    NOT NULL,
    step_key           VARCHAR(30)   NOT NULL,
    status             VARCHAR(20)   NOT NULL,
    attempt_count      INT           NOT NULL,
    max_attempts       INT           NOT NULL,
    last_error_code    VARCHAR(40)   NULL,
    summary            JSON          NULL,
    reopen_reason_code VARCHAR(40)   NULL,
    reviewer_message   VARCHAR(1000) NULL,
    completed_at       DATETIME(6)   NULL,
    version            BIGINT        NOT NULL,
    created_at         DATETIME(6)   NOT NULL,
    updated_at         DATETIME(6)   NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT application_step_uk UNIQUE (application_id, step_key),
    CONSTRAINT application_step_application_fk FOREIGN KEY (application_id) REFERENCES partner_application (id),
    CONSTRAINT application_step_status_chk CHECK (status IN ('NOT_STARTED', 'IN_PROGRESS', 'PENDING_EXTERNAL',
        'COMPLETED', 'FAILED', 'BLOCKED', 'LOCKED', 'REOPENED', 'NOT_APPLICABLE'))
);

CREATE TABLE application_event
(
    id             BINARY(16)  NOT NULL,
    application_id BINARY(16)  NOT NULL,
    event_type     VARCHAR(40) NOT NULL,
    step_key       VARCHAR(30) NULL,
    from_status    VARCHAR(30) NULL,
    to_status      VARCHAR(30) NULL,
    actor          VARCHAR(20) NOT NULL,
    occurred_at    DATETIME(6) NOT NULL,
    version        BIGINT      NOT NULL,
    created_at     DATETIME(6) NOT NULL,
    updated_at     DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT application_event_application_fk FOREIGN KEY (application_id) REFERENCES partner_application (id),
    INDEX application_event_application_idx (application_id, occurred_at)
);

CREATE TABLE idempotency_record
(
    id              BINARY(16)  NOT NULL,
    scope_id        BINARY(16)  NOT NULL,
    idem_key        VARCHAR(80) NOT NULL,
    request_hash    BINARY(32)  NOT NULL,
    response_status INT         NULL,
    response_etag   VARCHAR(40) NULL,
    response_body   MEDIUMTEXT  NULL,
    expires_at      DATETIME(6) NOT NULL,
    version         BIGINT      NOT NULL,
    created_at      DATETIME(6) NOT NULL,
    updated_at      DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT idempotency_record_uk UNIQUE (scope_id, idem_key),
    INDEX idempotency_record_expiry_idx (expires_at)
);
