-- Production predates these migrations: its schema was built by Django, which never creates
-- database-level defaults, so every DEFAULT declared in V1 exists on fresh databases (and in
-- tests) but not in production. An insert that omitted one of these columns would pass every
-- test and fail in production with a NOT NULL violation - that is how the weekly-awards job broke
-- (spybot_award.date, fixed in the job itself). Nothing relies on these defaults today; this
-- makes production match so nothing can in the future either.
--
-- Each ALTER is a single action: jOOQ's DDL parser, which generates the Kotlin schema from these
-- files, does not accept multi-action ALTER TABLE statements.
ALTER TABLE spybot_mergeduser ALTER COLUMN obsolete SET DEFAULT FALSE;
ALTER TABLE spybot_mergeduser ALTER COLUMN is_superuser SET DEFAULT FALSE;
ALTER TABLE tsuser ALTER COLUMN iscurrentlyonline SET DEFAULT FALSE;
ALTER TABLE spybot_steamid ALTER COLUMN steam_id SET DEFAULT 0;
ALTER TABLE spybot_userpasskey ALTER COLUMN enabled SET DEFAULT TRUE;
ALTER TABLE spybot_userpasskey ALTER COLUMN platform SET DEFAULT '';
ALTER TABLE spybot_userpasskey ALTER COLUMN added_on SET DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE spybot_award ALTER COLUMN type SET DEFAULT 'USER_OF_WEEK';

-- Same story for tsuseractivity.joined, which Django also left nullable. The recorder has always
-- written it (no row has NULL), so the constraint V1 declares can be applied for real.
ALTER TABLE tsuseractivity ALTER COLUMN joined SET DEFAULT FALSE;
ALTER TABLE tsuseractivity ALTER COLUMN joined SET NOT NULL;
