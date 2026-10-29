-- What the server saw of the live photo's blink (docs/OPEN_QUESTIONS.md C-12): CONFIRMED (eyes closed in the
-- blink frame, open in the photo), UNCONFIRMED (the same face, but the eyes could not be told apart: staff
-- look), NOT_CHECKED (no face check configured). The blink frame itself is never stored.
ALTER TABLE photo_location
    ADD COLUMN blink VARCHAR(20) NULL AFTER flags,
    ADD CONSTRAINT photo_location_blink_chk CHECK (blink IS NULL OR blink IN ('CONFIRMED', 'UNCONFIRMED', 'NOT_CHECKED'));
