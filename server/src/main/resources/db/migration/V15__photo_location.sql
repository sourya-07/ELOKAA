-- Where the live photo was taken (journey v2, ADR-0007): the browser's location at capture, required.
-- Coordinates are encrypted (a precise personal location); accuracy and flags are for staff review.
CREATE TABLE photo_location
(
    id             BINARY(16)    NOT NULL,
    application_id BINARY(16)    NOT NULL,
    document_id    BINARY(16)    NOT NULL,
    latitude_enc   VARBINARY(64) NOT NULL,
    longitude_enc  VARBINARY(64) NOT NULL,
    accuracy_m     DOUBLE        NOT NULL,
    captured_at    DATETIME(6)   NOT NULL,
    received_at    DATETIME(6)   NOT NULL,
    flags          VARCHAR(60)   NOT NULL,
    version        BIGINT        NOT NULL,
    created_at     DATETIME(6)   NOT NULL,
    updated_at     DATETIME(6)   NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT photo_location_document_uk UNIQUE (document_id),
    CONSTRAINT photo_location_application_fk FOREIGN KEY (application_id) REFERENCES partner_application (id),
    CONSTRAINT photo_location_document_fk FOREIGN KEY (document_id) REFERENCES stored_document (id),
    CONSTRAINT photo_location_accuracy_chk CHECK (accuracy_m > 0)
);
