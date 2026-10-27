-- Applicants (one per verified mobile), sign-in OTPs and sessions.
-- No OTP code is stored (the provider owns it); sessions store only SHA-256 hashes of tokens.
CREATE TABLE applicant
(
    id            BINARY(16)     NOT NULL,
    mobile_enc    VARBINARY(128) NOT NULL,
    mobile_hash   BINARY(32)     NOT NULL,
    mobile_masked VARCHAR(10)    NOT NULL,
    version       BIGINT         NOT NULL,
    created_at    DATETIME(6)    NOT NULL,
    updated_at    DATETIME(6)    NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT applicant_mobile_uk UNIQUE (mobile_hash)
);

CREATE TABLE otp_challenge
(
    id             BINARY(16)  NOT NULL,
    mobile_hash    BINARY(32)  NOT NULL,
    purpose        VARCHAR(20) NOT NULL,
    provider_ref   VARCHAR(64) NULL, -- NULL while the provider has not confirmed the send
    client_ip_hash BINARY(32)  NULL,
    expires_at     DATETIME(6) NOT NULL,
    attempts       INT         NOT NULL,
    max_attempts   INT         NOT NULL,
    consumed_at    DATETIME(6) NULL,
    version        BIGINT      NOT NULL,
    created_at     DATETIME(6) NOT NULL,
    updated_at     DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    INDEX otp_challenge_mobile_idx (mobile_hash, purpose, created_at),
    INDEX otp_challenge_ip_idx (client_ip_hash, created_at)
);

-- One row per mobile and per client IP, locked while an OTP send is checked against the limits,
-- so parallel requests are counted one after the other.
CREATE TABLE otp_send_guard
(
    kind       VARCHAR(10) NOT NULL,
    key_hash   BINARY(32)  NOT NULL,
    touched_at DATETIME(6) NOT NULL,
    PRIMARY KEY (kind, key_hash)
);

CREATE TABLE auth_session
(
    id                    BINARY(16)  NOT NULL,
    kind                  VARCHAR(20) NOT NULL,
    subject_id            BINARY(16)  NOT NULL,
    access_token_hash     BINARY(32)  NOT NULL,
    refresh_token_hash    BINARY(32)  NOT NULL,
    previous_refresh_hash BINARY(32)  NULL,
    access_expires_at     DATETIME(6) NOT NULL,
    idle_expires_at       DATETIME(6) NOT NULL,
    absolute_expires_at   DATETIME(6) NOT NULL,
    last_seen_at          DATETIME(6) NOT NULL,
    revoked_at            DATETIME(6) NULL,
    revoke_reason         VARCHAR(40) NULL,
    version               BIGINT      NOT NULL,
    created_at            DATETIME(6) NOT NULL,
    updated_at            DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT auth_session_access_uk UNIQUE (access_token_hash),
    CONSTRAINT auth_session_refresh_uk UNIQUE (refresh_token_hash),
    INDEX auth_session_previous_refresh_idx (previous_refresh_hash),
    INDEX auth_session_subject_idx (subject_id),
    CONSTRAINT auth_session_kind_chk CHECK (kind IN ('PARTNER_APPLICANT', 'STAFF'))
);
