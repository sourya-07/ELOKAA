-- Journey v2 foundation (ADR-0007).
-- data_hash: blind index of the data a step was last completed with, so redoing a step with the same
-- data (same PAN, same DigiLocker identity) does not reset the steps after it.
ALTER TABLE application_step
    ADD COLUMN data_hash BINARY(32) NULL AFTER completed_at;

-- Room for the rate-limit kinds of the new steps (EMAIL, EMAIL_IP, DIGILOCKER, ...).
ALTER TABLE otp_send_guard
    MODIFY COLUMN kind VARCHAR(20) NOT NULL;
