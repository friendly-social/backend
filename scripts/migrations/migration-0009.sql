CREATE TABLE activity_new_unreads (
    user_id BIGINT PRIMARY KEY,
    new_unread_id BIGINT
);
GRANT ALL ON activity_new_unreads TO friendly;

ALTER TABLE interests ADD id BIGSERIAL;

GRANT ALL ON interests_id_seq TO friendly;
