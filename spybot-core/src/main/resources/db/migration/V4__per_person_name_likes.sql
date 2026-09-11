-- Likes on generated names become per person: one row per (name, liker), enforced by the
-- database, so a name can be liked once and the like can be taken back. Logged-in users are
-- identified by their merged user; anonymous visitors by a long-lived cookie (VisitorIdFilter).
-- The old anonymous counter could not be attributed to anyone, so it is discarded and the count
-- is derived from this table from now on.
CREATE TABLE spybot_namelike (
    id BIGSERIAL PRIMARY KEY,
    name_id BIGINT NOT NULL REFERENCES spybot_likedname(id) ON DELETE CASCADE,
    merged_user_id BIGINT REFERENCES spybot_mergeduser(id) ON DELETE CASCADE,
    visitor_id VARCHAR(64),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT spybot_namelike_one_liker CHECK (
        (merged_user_id IS NOT NULL AND visitor_id IS NULL) OR (merged_user_id IS NULL AND visitor_id IS NOT NULL)
    ),
    CONSTRAINT spybot_namelike_user_once UNIQUE (name_id, merged_user_id),
    CONSTRAINT spybot_namelike_visitor_once UNIQUE (name_id, visitor_id)
);

DELETE FROM spybot_likedname;
ALTER TABLE spybot_likedname DROP COLUMN likes;
ALTER TABLE spybot_likedname DROP COLUMN last_liked_at;
