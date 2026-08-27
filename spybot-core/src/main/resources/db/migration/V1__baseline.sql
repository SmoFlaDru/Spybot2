CREATE TABLE spybot_mergeduser (
    id BIGSERIAL PRIMARY KEY,
    password VARCHAR(128) NOT NULL,
    last_login TIMESTAMPTZ NULL,
    name VARCHAR(128) NOT NULL,
    obsolete BOOLEAN NOT NULL DEFAULT FALSE,
    is_superuser BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE TABLE tschannel (
    id SERIAL PRIMARY KEY,
    name VARCHAR(64),
    "order" INTEGER NOT NULL
);


CREATE TABLE tsuser (
    id SERIAL PRIMARY KEY,
    name VARCHAR(128),
    clientid INTEGER NOT NULL,
    iscurrentlyonline BOOLEAN NOT NULL DEFAULT FALSE,
    merged_user_id BIGINT NULL REFERENCES spybot_mergeduser (id) ON DELETE SET NULL
);

CREATE TABLE tsid (
    tsid VARCHAR(32) PRIMARY KEY,
    tsuserid INTEGER NULL REFERENCES tsuser (id)
);

CREATE TABLE tsuseractivity (
    id SERIAL PRIMARY KEY,
    tsuserid INTEGER NULL REFERENCES tsuser (id),
    starttime TIMESTAMPTZ NULL,
    endtime TIMESTAMPTZ NULL,
    joined BOOLEAN NOT NULL DEFAULT FALSE,
    discid INTEGER NULL,
    cid INTEGER NOT NULL REFERENCES tschannel (id)
);

CREATE INDEX tsuseractivity_starttime_idx ON tsuseractivity (starttime);

CREATE TABLE hourlyactivity (
    id BIGSERIAL PRIMARY KEY,
    datetime TIMESTAMPTZ NOT NULL,
    activity_hours DOUBLE PRECISION NOT NULL
);

CREATE INDEX hourlyactivity_datetime_idx ON hourlyactivity (datetime);

CREATE TABLE spybot_newsevent (
    id BIGSERIAL PRIMARY KEY,
    text VARCHAR(1024) NOT NULL,
    website_link VARCHAR(256),
    date TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE spybot_loginlink (
    id BIGSERIAL PRIMARY KEY,
    code CHAR(32) NOT NULL UNIQUE,
    user_id BIGINT NOT NULL REFERENCES spybot_mergeduser (id) ON DELETE CASCADE
);

CREATE TABLE spybot_steamid (
    id BIGSERIAL PRIMARY KEY,
    steam_id BIGINT NOT NULL DEFAULT 0,
    account_name VARCHAR(128),
    merged_user_id BIGINT NOT NULL REFERENCES spybot_mergeduser (id) ON DELETE CASCADE
);

CREATE TABLE spybot_userpasskey (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES spybot_mergeduser (id) ON DELETE CASCADE,
    name VARCHAR(255) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    platform VARCHAR(255) NOT NULL DEFAULT '',
    added_on TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_used TIMESTAMPTZ NULL,
    credential_id VARCHAR(255) NOT NULL UNIQUE,
    token VARCHAR(1024) NOT NULL
);

CREATE TABLE spybot_award (
    id BIGSERIAL PRIMARY KEY,
    date TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    type VARCHAR(64) NOT NULL DEFAULT 'USER_OF_WEEK',
    points INTEGER NOT NULL,
    tsuser_id INTEGER NULL REFERENCES tsuser (id) ON DELETE CASCADE,
    merged_user_id BIGINT NOT NULL REFERENCES spybot_mergeduser (id) ON DELETE CASCADE
);

CREATE TABLE spybot_queuedclientmessage (
    id BIGSERIAL PRIMARY KEY,
    tsuser_id INTEGER NULL REFERENCES tsuser (id) ON DELETE CASCADE,
    merged_user_id BIGINT NOT NULL REFERENCES spybot_mergeduser (id) ON DELETE CASCADE,
    text VARCHAR(1024) NOT NULL,
    type VARCHAR(128) NOT NULL,
    date DATE NOT NULL DEFAULT CURRENT_DATE
);

ALTER TABLE spybot_queuedclientmessage
    ADD CONSTRAINT constraint_unique_type_user UNIQUE (tsuser_id, type);
