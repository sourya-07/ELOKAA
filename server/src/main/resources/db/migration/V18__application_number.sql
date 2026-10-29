-- A numeric application number for people (applicants, staff, support calls): 10 digits, unique,
-- given by the database when the application is created. The UUID primary key and the ptn_ public id
-- stay the identifiers in the API, URLs and the audit log; nothing looks an application up by this
-- number. It starts at 1000000001, so it is always 10 digits and never looks like a mobile number
-- (6-9 first) or an 8-digit Eko CSP code. Gaps are fine (a rolled-back insert uses a number up).
-- MySQL allows no CHECK constraint on an AUTO_INCREMENT column; the start value keeps it in range.

-- 1. Existing rows get numbers in the order they were created.
ALTER TABLE partner_application
    ADD COLUMN application_number BIGINT NULL AFTER public_id;

UPDATE partner_application p
    JOIN (SELECT id, ROW_NUMBER() OVER (ORDER BY created_at, id) AS rn FROM partner_application) r ON r.id = p.id
SET p.application_number = 1000000000 + r.rn;

-- 2. From now on the database gives the number, unique under any number of parallel inserts.
ALTER TABLE partner_application
    MODIFY COLUMN application_number BIGINT NOT NULL AUTO_INCREMENT,
    ADD CONSTRAINT partner_application_number_uk UNIQUE (application_number);

-- 3. The next number continues above the back-filled ones (InnoDB never sets it below MAX + 1).
ALTER TABLE partner_application AUTO_INCREMENT = 1000000001;
