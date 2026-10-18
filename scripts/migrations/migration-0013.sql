
ALTER TABLE activity ADD
    indirect_replies_common_denominator_id BIGINT;

UPDATE activity a SET
    indirect_replies_common_denominator_id = a.indirect_replies_post_id;
