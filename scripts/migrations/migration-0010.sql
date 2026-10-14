
-- NOT RAN; TODO: UPDATE WHEN RAN
UPDATE community_posts_path
SET
    reply_to_depth = post_depth,
    post_depth = reply_to_depth;

CREATE TABLE community_new_replies (
    user_id BIGINT,
    post_id BIGINT,
    new_reply BIGINT,
    PRIMARY KEY (user_id, post_id)
);

CREATE TABLE community_viewers (
    user_id BIGINT,
    post_id BIGINT,
    PRIMARY KEY (user_id, post_id)
);
