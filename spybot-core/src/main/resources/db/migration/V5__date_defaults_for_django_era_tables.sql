-- The production database was created by the old Django app, where column defaults lived in
-- Python, not in the schema. V1__baseline declares DEFAULT CURRENT_TIMESTAMP / CURRENT_DATE on
-- these date columns, but a baselined database never ran V1, so production has no defaults and
-- an insert that omits the column fails with a not-null violation (seen in the weekly awards
-- job). The inserts now set the dates explicitly; this brings the schema in line with the
-- baseline so the two can't drift apart again. No-op on databases created from V1.
ALTER TABLE spybot_award ALTER COLUMN date SET DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE spybot_newsevent ALTER COLUMN date SET DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE spybot_queuedclientmessage ALTER COLUMN date SET DEFAULT CURRENT_DATE;
