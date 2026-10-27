-- Spring Modulith event publication registry (transactional outbox for module events).
-- Based on spring-modulith-events-jdbc 2.1 schemas/v2/schema-mysql.sql, adapted to the JPA mapping.
CREATE TABLE event_publication
(
    id                     BINARY(16)    NOT NULL,
    listener_id            VARCHAR(512)  NOT NULL,
    event_type             VARCHAR(512)  NOT NULL,
    serialized_event       VARCHAR(4000) NOT NULL,
    publication_date       TIMESTAMP(6)  NOT NULL,
    completion_date        TIMESTAMP(6)  NULL DEFAULT NULL,
    status                 VARCHAR(20)   NULL,
    completion_attempts    INT           NULL,
    last_resubmission_date TIMESTAMP(6)  NULL DEFAULT NULL,
    PRIMARY KEY (id),
    INDEX event_publication_by_completion_date_idx (completion_date)
);
