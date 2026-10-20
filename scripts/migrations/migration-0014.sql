ALTER TABLE activity
    DROP indirect_replies_common_denominator_id,
    DROP indirect_replies_new_unread_id;

DELETE FROM activity WHERE type = 1;

ALTER TABLE community_new_unreads RENAME TO community_unreads;
ALTER TABLE community_unreads
    ADD is_read BOOL DEFAULT false;
