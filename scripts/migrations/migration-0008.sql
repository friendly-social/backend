CREATE TABLE alerts (
    id BIGSERIAL,
    type INTEGER NOT NULL,
    instant TIMESTAMPTZ NOT NULL,
    user_id BIGINT
);

GRANT ALL ON alerts TO friendly;
GRANT ALL ON alerts_id_seq TO friendly;

DELETE FROM files WHERE pending = TRUE;

ALTER TABLE files
    ALTER pending SET DEFAULT true,
    ADD owner_id BIGINT,
    ALTER size SET NOT NULL,
    ALTER access_hash SET NOT NULL,
    ALTER timestamp SET NOT NULL,
    DROP owner_ip;

ALTER TABLE files RENAME timestamp TO instant;

UPDATE files SET owner_id = 0;
ALTER TABLE files ALTER owner_id SET NOT NULL;

CREATE TABLE files_preupload (
    id BIGSERIAL,
    size BIGINT,
    instant TIMESTAMPTZ,
    access_hash VARCHAR(256),
    pending BOOL DEFAULT TRUE,
    mark_for_deletion BOOL DEFAULT FALSE
);

GRANT ALL ON files_preupload TO friendly;
GRANT ALL ON files_preupload_id_seq TO friendly;
