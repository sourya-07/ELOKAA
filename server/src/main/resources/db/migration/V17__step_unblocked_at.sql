-- Staff can give a blocked step its tries back after following up (journey v2, D5). Failures before
-- this time no longer count against the voter ID / driving licence tries.
ALTER TABLE application_step
    ADD COLUMN unblocked_at DATETIME(6) NULL AFTER data_hash;
