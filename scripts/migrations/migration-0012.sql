ALTER TABLE activity
    ADD indirect_replies_post_id BIGINT,
    ADD indirect_replies_new_unread_id BIGINT;

CREATE TABLE community_subscribers (
    post_id BIGINT,
    user_id BIGINT,
    PRIMARY KEY (post_id, user_id)
);
