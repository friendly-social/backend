DROP TABLE community_new_replies;

CREATE TABLE community_new_unreads (
    user_id BIGINT,
    post_id BIGINT,
    PRIMARY KEY (user_id, post_id)
);
