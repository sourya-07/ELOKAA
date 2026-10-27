-- Home address (street-level parts encrypted) and voter ID / driving licence numbers (encrypted).
CREATE TABLE partner_address
(
    id             BINARY(16)     NOT NULL,
    application_id BINARY(16)     NOT NULL,
    line1_enc      VARBINARY(512) NOT NULL,
    line2_enc      VARBINARY(512) NULL,
    city_enc       VARBINARY(256) NOT NULL,
    district       VARCHAR(60)    NULL,
    state          VARCHAR(60)    NOT NULL,
    pincode        CHAR(6)        NOT NULL,
    source         VARCHAR(20)    NOT NULL,
    status         VARCHAR(30)    NOT NULL,
    version        BIGINT         NOT NULL,
    created_at     DATETIME(6)    NOT NULL,
    updated_at     DATETIME(6)    NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT partner_address_application_uk UNIQUE (application_id),
    CONSTRAINT partner_address_application_fk FOREIGN KEY (application_id) REFERENCES partner_application (id),
    CONSTRAINT partner_address_source_chk CHECK (source IN ('DIGILOCKER', 'SELF_DECLARED'))
);

CREATE TABLE partner_id_number
(
    id             BINARY(16)     NOT NULL,
    application_id BINARY(16)     NOT NULL,
    id_type        VARCHAR(20)    NOT NULL,
    number_enc     VARBINARY(128) NOT NULL,
    number_masked  VARCHAR(20)    NOT NULL,
    version        BIGINT         NOT NULL,
    created_at     DATETIME(6)    NOT NULL,
    updated_at     DATETIME(6)    NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT partner_id_number_uk UNIQUE (application_id, id_type),
    CONSTRAINT partner_id_number_application_fk FOREIGN KEY (application_id) REFERENCES partner_application (id),
    CONSTRAINT partner_id_number_type_chk CHECK (id_type IN ('VOTER_ID', 'DRIVING_LICENCE'))
);
