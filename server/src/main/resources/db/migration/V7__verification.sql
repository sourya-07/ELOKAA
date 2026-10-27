-- Identity checks (PAN, Aadhaar) and DigiLocker trips. Personal values are encrypted (*_enc);
-- the PAN also has a blind index (value_hash) and a masked copy. Never the full Aadhaar number.
CREATE TABLE verification
(
    id             BINARY(16)     NOT NULL,
    application_id BINARY(16)     NOT NULL,
    kind           VARCHAR(20)    NOT NULL,
    method         VARCHAR(20)    NOT NULL,
    status         VARCHAR(30)    NOT NULL,
    provider       VARCHAR(30)    NULL,
    provider_ref   VARCHAR(64)    NULL,
    value_enc      VARBINARY(128) NULL,
    value_hash     BINARY(32)     NULL,
    value_masked   VARCHAR(20)    NULL,
    name_enc       VARBINARY(512) NULL,
    dob_enc        VARBINARY(64)  NULL,
    failure_code   VARCHAR(40)    NULL,
    decided_at     DATETIME(6)    NULL,
    version        BIGINT         NOT NULL,
    created_at     DATETIME(6)    NOT NULL,
    updated_at     DATETIME(6)    NOT NULL,
    PRIMARY KEY (id),
    -- the same provider answer cannot be recorded twice (replayed DigiLocker returns)
    CONSTRAINT verification_provider_ref_uk UNIQUE (provider, provider_ref),
    CONSTRAINT verification_application_fk FOREIGN KEY (application_id) REFERENCES partner_application (id),
    INDEX verification_application_idx (application_id, kind, created_at),
    CONSTRAINT verification_kind_chk CHECK (kind IN ('PAN', 'AADHAAR')),
    CONSTRAINT verification_method_chk CHECK (method IN ('PROVIDER', 'DIGILOCKER', 'MANUAL'))
);

CREATE TABLE digilocker_session
(
    id               BINARY(16)  NOT NULL,
    application_id   BINARY(16)  NOT NULL,
    verification_ref VARCHAR(20) NOT NULL,
    provider_ref     VARCHAR(64) NOT NULL,
    state_hash       BINARY(32)  NOT NULL,
    expires_at       DATETIME(6) NOT NULL,
    consumed_at      DATETIME(6) NULL,
    version          BIGINT      NOT NULL,
    created_at       DATETIME(6) NOT NULL,
    updated_at       DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT digilocker_session_ref_uk UNIQUE (verification_ref),
    CONSTRAINT digilocker_session_state_uk UNIQUE (state_hash),
    CONSTRAINT digilocker_session_application_fk FOREIGN KEY (application_id) REFERENCES partner_application (id)
);
