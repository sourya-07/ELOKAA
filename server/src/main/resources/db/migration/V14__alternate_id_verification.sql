-- Journey v2: the voter ID or driving licence is checked with its government record (EPS) and
-- against the DigiLocker identity (ADR-0007). The number is no longer only typed and eyeballed.
ALTER TABLE verification
    DROP CONSTRAINT verification_kind_chk,
    ADD CONSTRAINT verification_kind_chk CHECK (kind IN ('PAN', 'AADHAAR', 'VOTER_ID', 'DRIVING_LICENCE')),
    ADD INDEX verification_kind_value_idx (kind, value_hash);

-- One open, approved or ineligible application per voter ID and per driving licence (the PAN rule),
-- enforced by the database so two parallel checks cannot both win.
ALTER TABLE partner_application
    ADD COLUMN voter_id_hash BINARY(32) NULL AFTER email_masked,
    ADD COLUMN driving_licence_hash BINARY(32) NULL AFTER voter_id_hash,
    ADD COLUMN blocking_voter_key BINARY(32) AS (CASE
        WHEN status IN ('REJECTED', 'WITHDRAWN', 'EXPIRED') THEN NULL
        ELSE voter_id_hash END) STORED,
    ADD COLUMN blocking_dl_key BINARY(32) AS (CASE
        WHEN status IN ('REJECTED', 'WITHDRAWN', 'EXPIRED') THEN NULL
        ELSE driving_licence_hash END) STORED,
    ADD CONSTRAINT partner_application_blocking_voter_uk UNIQUE (blocking_voter_key),
    ADD CONSTRAINT partner_application_blocking_dl_uk UNIQUE (blocking_dl_key);

-- The new step for existing applications: still being filled in, it is asked for; already submitted,
-- frozen (their numbers were typed and checked by staff); closed, not applicable. 6 tries: 3 per document.
INSERT INTO application_step (id, application_id, step_key, status, attempt_count, max_attempts, version, created_at,
                              updated_at)
SELECT UUID_TO_BIN(UUID()), a.id, 'ALTERNATE_ID',
       CASE
           WHEN a.status = 'IN_PROGRESS' THEN 'NOT_STARTED'
           WHEN a.status IN ('SUBMITTED', 'UNDER_REVIEW', 'ON_HOLD', 'CORRECTION_REQUIRED') THEN 'LOCKED'
           ELSE 'NOT_APPLICABLE' END,
       0, 6, 0, NOW(6), NOW(6)
FROM partner_application a
WHERE NOT EXISTS (SELECT 1 FROM application_step s WHERE s.application_id = a.id AND s.step_key = 'ALTERNATE_ID');
