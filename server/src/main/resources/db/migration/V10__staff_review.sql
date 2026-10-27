-- Eko staff accounts and roles, and every review action on an application (maker-checker).
CREATE TABLE staff_user
(
    id           BINARY(16)     NOT NULL,
    email_enc    VARBINARY(512) NOT NULL,
    email_hash   BINARY(32)     NOT NULL,
    email_masked VARCHAR(254)   NOT NULL,
    display_name VARCHAR(100)   NOT NULL,
    mobile_hash  BINARY(32)     NULL,
    active       BOOLEAN        NOT NULL,
    version      BIGINT         NOT NULL,
    created_at   DATETIME(6)    NOT NULL,
    updated_at   DATETIME(6)    NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT staff_user_email_uk UNIQUE (email_hash),
    CONSTRAINT staff_user_mobile_uk UNIQUE (mobile_hash)
);

CREATE TABLE staff_role_grant
(
    staff_id BINARY(16)  NOT NULL,
    role     VARCHAR(20) NOT NULL,
    PRIMARY KEY (staff_id, role),
    CONSTRAINT staff_role_grant_staff_fk FOREIGN KEY (staff_id) REFERENCES staff_user (id),
    CONSTRAINT staff_role_grant_role_chk CHECK (role IN ('REVIEWER', 'APPROVER'))
);

CREATE TABLE review_action
(
    id             BINARY(16)    NOT NULL,
    application_id BINARY(16)    NOT NULL,
    staff_id       BINARY(16)    NOT NULL,
    action         VARCHAR(30)   NOT NULL,
    decision       VARCHAR(20)   NULL,
    reason_code    VARCHAR(40)   NULL,
    note           VARCHAR(1000) NULL,
    target_ref     VARCHAR(40)   NULL,
    occurred_at    DATETIME(6)   NOT NULL,
    version        BIGINT        NOT NULL,
    created_at     DATETIME(6)   NOT NULL,
    updated_at     DATETIME(6)   NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT review_action_application_fk FOREIGN KEY (application_id) REFERENCES partner_application (id),
    CONSTRAINT review_action_staff_fk FOREIGN KEY (staff_id) REFERENCES staff_user (id),
    INDEX review_action_application_idx (application_id, occurred_at)
);
