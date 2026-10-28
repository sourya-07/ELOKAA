-- Journey v2, identity (ADR-0007): DigiLocker is the only way to verify Aadhaar; the step-by-step
-- path and its VERIFICATION_METHOD step are retired.
--
-- Guard: an open application on the step-by-step path cannot be moved over automatically (one under
-- review could still be sent back for a PAN correction, which now needs a DigiLocker identity). If one
-- exists, this migration stops here with the constraint name below; such applications must be decided or
-- closed first. Deployed environments have none yet. The table is dropped first so a re-run works
-- (MySQL commits CREATE TABLE even when the INSERT below fails).
DROP TABLE IF EXISTS journey_v2_guard;
CREATE TABLE journey_v2_guard
(
    manual_applications_still_open INT NOT NULL,
    CONSTRAINT journey_v2_no_open_step_by_step_application CHECK (manual_applications_still_open = 0)
);
INSERT INTO journey_v2_guard (manual_applications_still_open)
SELECT COUNT(*)
FROM partner_application
WHERE method = 'MANUAL'
  AND status IN ('IN_PROGRESS', 'CORRECTION_REQUIRED', 'SUBMITTED', 'UNDER_REVIEW', 'ON_HOLD');
DROP TABLE journey_v2_guard;

-- Applications not yet past the choice start on DigiLocker.
UPDATE partner_application
SET method = 'DIGILOCKER'
WHERE method IS NULL;

-- The retired step. Submitted step-by-step applications keep their (frozen) Aadhaar evidence and images.
DELETE
FROM application_step
WHERE step_key = 'VERIFICATION_METHOD';
