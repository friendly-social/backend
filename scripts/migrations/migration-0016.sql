ALTER TABLE friend_tokens ADD expiration TIMESTAMPTZ;
TRUNCATE friend_tokens;
