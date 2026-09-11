-- Likes for names proposed by the Steam name generator (/namegen). One row per distinct name;
-- liking a name that is already here bumps its counter instead of adding another row.
CREATE TABLE spybot_likedname (
    id BIGSERIAL PRIMARY KEY,
    display_name VARCHAR(128) NOT NULL UNIQUE,
    real_name VARCHAR(128) NOT NULL,
    slang VARCHAR(64) NOT NULL,
    likes INTEGER NOT NULL DEFAULT 0,
    first_liked_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_liked_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
