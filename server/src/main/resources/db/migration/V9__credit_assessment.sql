-- CIBIL assessments: only the score, outcome and the bureau's reference. Never the report.
CREATE TABLE credit_assessment
(
    id             BINARY(16)  NOT NULL,
    application_id BINARY(16)  NOT NULL,
    pan_hash       BINARY(32)  NOT NULL,
    provider       VARCHAR(30) NOT NULL,
    provider_ref   VARCHAR(64) NULL,
    outcome        VARCHAR(20) NOT NULL,
    score          SMALLINT    NULL,
    failure_code   VARCHAR(40) NULL,
    assessed_at    DATETIME(6) NOT NULL,
    version        BIGINT      NOT NULL,
    created_at     DATETIME(6) NOT NULL,
    updated_at     DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT credit_assessment_application_fk FOREIGN KEY (application_id) REFERENCES partner_application (id),
    INDEX credit_assessment_application_idx (application_id, assessed_at),
    CONSTRAINT credit_assessment_outcome_chk CHECK (outcome IN ('ELIGIBLE', 'INELIGIBLE', 'UNABLE_TO_ASSESS')),
    CONSTRAINT credit_assessment_score_chk CHECK (score IS NULL OR score BETWEEN 300 AND 900),
    -- the outcome can never disagree with the score (ADR-0005: 700 or more)
    CONSTRAINT credit_assessment_rule_chk CHECK (
        (outcome = 'ELIGIBLE' AND score IS NOT NULL AND score >= 700)
        OR (outcome = 'INELIGIBLE' AND score IS NOT NULL AND score < 700)
        OR (outcome = 'UNABLE_TO_ASSESS'))
);
