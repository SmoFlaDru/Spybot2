-- The production schema, which predates these migrations, has tsuser.clientid nullable and most
-- rows (identities that last connected before the recorder started recording client ids) have
-- it NULL. V1 declared it NOT NULL, so the jOOQ code generated from the migrations - and every
-- fresh database - disagreed with production; the admin user list crashed on the first NULL.
-- A no-op on production, aligns everything else.
ALTER TABLE tsuser ALTER COLUMN clientid DROP NOT NULL;
