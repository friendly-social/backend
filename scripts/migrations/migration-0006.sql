ALTER TABLE friends ADD COLUMN decline_times INTEGER;
ALTER TABLE friends ADD COLUMN decline_forget_random BIGINT;
ALTER TABLE friends ADD COLUMN instant TIMESTAMP;

UPDATE friends SET decline_times = 0, decline_forget_random = 0, instant = NOW();
