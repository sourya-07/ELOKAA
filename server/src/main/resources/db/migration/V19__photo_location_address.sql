-- The live photo's approximate address, looked up from its browser-reported coordinates (OpenStreetMap
-- Nominatim, docs/OPEN_QUESTIONS.md C-11). Encrypted like the coordinates; NULL when no lookup is
-- configured or it failed (the upload never waits for it). Not a checked address.
ALTER TABLE photo_location
    ADD COLUMN address_enc VARBINARY(1024) NULL AFTER longitude_enc;
