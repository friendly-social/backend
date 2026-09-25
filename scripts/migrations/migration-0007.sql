ALTER TABLE community_posts_path RENAME COLUMN depth TO reply_depth;

ALTER TABLE community_posts_path ADD COLUMN post_depth BIGINT;

UPDATE community_posts_path post
SET post_depth = max.depth
FROM(
    SELECT post_id, MAX(reply_depth) AS depth
    FROM community_posts_path
    GROUP BY post_id
) max
WHERE max.post_id = post.post_id;

ALTER TABLE community_posts_path RENAME COLUMN reply_depth TO reply_to_depth;

UPDATE community_posts_path SET reply_to_depth = reply_to_depth - 1;
