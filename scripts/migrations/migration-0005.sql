ALTER TABLE activity ADD is_read BOOL DEFAULT FALSE;
UPDATE activity SET is_read = TRUE;
