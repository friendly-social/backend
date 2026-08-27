ALTER TABLE notifications ADD is_pending BOOL DEFAULT true;
UPDATE notifications SET is_pending = FALSE;
