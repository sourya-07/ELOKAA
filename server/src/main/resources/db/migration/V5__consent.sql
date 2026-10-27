-- What each applicant agreed to, per purpose, and when (append-only; evidence under the DPDP Act).
CREATE TABLE consent_record
(
    id             BINARY(16)  NOT NULL,
    application_id BINARY(16)  NOT NULL,
    purpose        VARCHAR(20) NOT NULL,
    text_version   VARCHAR(20) NOT NULL,
    accepted_at    DATETIME(6) NOT NULL,
    client_ip_hash BINARY(32)  NULL,
    version        BIGINT      NOT NULL,
    created_at     DATETIME(6) NOT NULL,
    updated_at     DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT consent_record_application_fk FOREIGN KEY (application_id) REFERENCES partner_application (id),
    INDEX consent_record_application_idx (application_id, purpose, accepted_at),
    CONSTRAINT consent_record_purpose_chk CHECK (purpose IN ('DATA_PROCESSING', 'DIGILOCKER', 'CREDIT_CHECK'))
);
