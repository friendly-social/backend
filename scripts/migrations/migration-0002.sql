ALTER TABLE community_posts ADD type integer DEFAULT 0;
UPDATE community_posts SET type = 0;

ALTER TABLE community_posts RENAME owner_id TO plain_owner_id;
ALTER TABLE community_posts ALTER plain_owner_id DROP NOT NULL;

ALTER TABLE community_posts RENAME text TO plain_text;
ALTER TABLE community_posts ALTER plain_text DROP NOT NULL;

ALTER TABLE community_posts RENAME edited TO plain_edited;
ALTER TABLE community_posts ALTER plain_text DROP NOT NULL;

ALTER TABLE notifications ADD new_reply_post_id BIGINT;
