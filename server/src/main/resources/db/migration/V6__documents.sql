-- Uploaded files (bytes encrypted in DocumentStorage, never in MySQL) and their links to applications.
CREATE TABLE stored_document
(
    id           BINARY(16)   NOT NULL,
    owner_id     BINARY(16)   NOT NULL,
    kind         VARCHAR(30)  NOT NULL,
    file_name    VARCHAR(120) NOT NULL,
    content_type VARCHAR(50)  NOT NULL,
    size_bytes   BIGINT       NOT NULL,
    width        INT          NOT NULL,
    height       INT          NOT NULL,
    sha256       BINARY(32)   NOT NULL,
    storage_key  VARCHAR(36)  NOT NULL,
    version      BIGINT       NOT NULL,
    created_at   DATETIME(6)  NOT NULL,
    updated_at   DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT stored_document_key_uk UNIQUE (storage_key),
    INDEX stored_document_owner_idx (owner_id, kind),
    CONSTRAINT stored_document_size_chk CHECK (size_bytes > 0 AND size_bytes <= 10485760),
    CONSTRAINT stored_document_type_chk CHECK (content_type IN ('image/jpeg', 'application/pdf'))
);

CREATE TABLE application_document
(
    id             BINARY(16)  NOT NULL,
    application_id BINARY(16)  NOT NULL,
    document_id    BINARY(16)  NOT NULL,
    kind           VARCHAR(30) NOT NULL,
    status         VARCHAR(30) NOT NULL,
    reject_reason  VARCHAR(40) NULL,
    removed_at     DATETIME(6) NULL,
    decided_at     DATETIME(6) NULL,
    version        BIGINT      NOT NULL,
    created_at     DATETIME(6) NOT NULL,
    updated_at     DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT application_document_document_uk UNIQUE (document_id),
    CONSTRAINT application_document_application_fk FOREIGN KEY (application_id) REFERENCES partner_application (id),
    CONSTRAINT application_document_stored_fk FOREIGN KEY (document_id) REFERENCES stored_document (id),
    INDEX application_document_application_idx (application_id, removed_at, kind)
);
