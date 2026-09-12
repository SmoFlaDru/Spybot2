-- Passkeys move to Spring Security's WebAuthn support, which needs the full credential record
-- (signature counter, backup flags, transports, attestation) rather than the old opaque token.
-- The four legacy rows were registered with a scheme that isn't carried over (AGENTS.md: existing
-- passkeys are disposable), so the table is cleared instead of migrated.
DELETE FROM spybot_userpasskey;

-- A WebAuthn user handle is the opaque id an authenticator stores inside a passkey and returns on
-- login. One user can own several handles: merging two accounts moves the source's handles to the
-- target, so passkeys registered before the merge keep resolving to the merged account.
CREATE TABLE spybot_webauthn_user_handle (
    handle VARCHAR(255) PRIMARY KEY,
    merged_user_id BIGINT NOT NULL REFERENCES spybot_mergeduser (id) ON DELETE CASCADE,
    created TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX spybot_webauthn_user_handle_user_idx ON spybot_webauthn_user_handle (merged_user_id);

-- One action per statement: jOOQ's DDL parser, which generates the Kotlin table classes from
-- these files, doesn't support multi-action ALTER TABLE.
ALTER TABLE spybot_userpasskey DROP COLUMN token;
ALTER TABLE spybot_userpasskey ADD COLUMN user_handle VARCHAR(255) NOT NULL REFERENCES spybot_webauthn_user_handle (handle);
ALTER TABLE spybot_userpasskey ADD COLUMN public_key_cose VARCHAR(2048) NOT NULL;
ALTER TABLE spybot_userpasskey ADD COLUMN signature_count BIGINT NOT NULL DEFAULT 0;
ALTER TABLE spybot_userpasskey ADD COLUMN uv_initialized BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE spybot_userpasskey ADD COLUMN transports VARCHAR(255) NOT NULL DEFAULT '';
ALTER TABLE spybot_userpasskey ADD COLUMN backup_eligible BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE spybot_userpasskey ADD COLUMN backup_state BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE spybot_userpasskey ADD COLUMN aaguid VARCHAR(36) NOT NULL DEFAULT '';
-- Spring Security reads the credential's public key back out of the attestation object on
-- every login, so it is required, not optional.
ALTER TABLE spybot_userpasskey ADD COLUMN attestation_object TEXT NOT NULL;
ALTER TABLE spybot_userpasskey ADD COLUMN attestation_client_data_json TEXT NOT NULL;
