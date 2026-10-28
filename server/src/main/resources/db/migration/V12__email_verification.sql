-- Email verification (journey v2, ADR-0007). EPS has no email OTP, so the code is ours: only a
-- keyed hash of it is stored, the address is encrypted.
CREATE TABLE email_verification_challenge
(
    id             BINARY(16)     NOT NULL,
    subject_id     BINARY(16)     NOT NULL, -- the application the code belongs to
    email_enc      VARBINARY(512) NOT NULL,
    email_hash     BINARY(32)     NOT NULL,
    code_hash      BINARY(32)     NULL,
    client_ip_hash BINARY(32)     NULL,
    expires_at     DATETIME(6)    NOT NULL,
    attempts       INT            NOT NULL,
    max_attempts   INT            NOT NULL,
    consumed_at    DATETIME(6)    NULL,
    version        BIGINT         NOT NULL,
    created_at     DATETIME(6)    NOT NULL,
    updated_at     DATETIME(6)    NOT NULL,
    PRIMARY KEY (id),
    INDEX email_challenge_email_idx (email_hash, created_at),
    INDEX email_challenge_ip_idx (client_ip_hash, created_at),
    INDEX email_challenge_subject_idx (subject_id, created_at)
);

-- The verified address of an application; one open, approved or ineligible application per address
-- (the same rule as the PAN), enforced by the database so two parallel verifications cannot both win.
ALTER TABLE partner_application
    ADD COLUMN email_enc VARBINARY(512) NULL AFTER pan_hash,
    ADD COLUMN email_hash BINARY(32) NULL AFTER email_enc,
    ADD COLUMN email_masked VARCHAR(80) NULL AFTER email_hash,
    ADD COLUMN blocking_email_key BINARY(32) AS (CASE
        WHEN status IN ('REJECTED', 'WITHDRAWN', 'EXPIRED') THEN NULL
        ELSE email_hash END) STORED,
    ADD CONSTRAINT partner_application_blocking_email_uk UNIQUE (blocking_email_key);

-- The new step for applications that already exist: still being filled in, the applicant verifies an
-- email next; already submitted, it is frozen like the other steps (no email on file); closed, not applicable.
INSERT INTO application_step (id, application_id, step_key, status, attempt_count, max_attempts, version, created_at,
                              updated_at)
SELECT UUID_TO_BIN(UUID()), a.id, 'EMAIL_VERIFICATION',
       CASE
           WHEN a.status = 'IN_PROGRESS' THEN 'NOT_STARTED'
           WHEN a.status IN ('SUBMITTED', 'UNDER_REVIEW', 'ON_HOLD', 'CORRECTION_REQUIRED') THEN 'LOCKED'
           ELSE 'NOT_APPLICABLE' END,
       0, 3, 0, NOW(6), NOW(6)
FROM partner_application a
WHERE NOT EXISTS (SELECT 1 FROM application_step s WHERE s.application_id = a.id AND s.step_key = 'EMAIL_VERIFICATION');
