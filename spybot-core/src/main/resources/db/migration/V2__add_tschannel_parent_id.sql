-- TS3's channel_order field is the id of the previous sibling channel at the same depth (0 for
-- the first child of a parent) - a linked-list pointer, not a flat display rank. Reconstructing
-- the actual TeamSpeak channel order requires walking that list per parent, which needs to know
-- each channel's parent.
ALTER TABLE tschannel ADD COLUMN pid INTEGER NOT NULL DEFAULT 0;
