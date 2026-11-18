-- User credentials (issue #61): forced password change, session revocation stamp, password reset tokens.
--
-- Reset is requested by email address, so an address must identify one account. Existing rows that
-- share an address (case-insensitive) stop the migration; the operator gives each active user a
-- distinct address or clears it, then reruns.

UPDATE users SET email = NULL WHERE email IS NOT NULL AND btrim(email) = '';

DO $$
DECLARE
    v_duplicates TEXT;
BEGIN
    SELECT string_agg(address, ', ') INTO v_duplicates
      FROM (SELECT lower(email) AS address
              FROM users
             WHERE email IS NOT NULL AND deleted_at IS NULL
             GROUP BY lower(email)
            HAVING count(*) > 1) d;

    IF v_duplicates IS NOT NULL THEN
        RAISE EXCEPTION 'V022: several users share an email address (%). Give each active user a distinct '
            'address or clear it, then rerun.', v_duplicates;
    END IF;
END $$;

CREATE UNIQUE INDEX uk_users_email_lower ON users (lower(email))
    WHERE email IS NOT NULL AND deleted_at IS NULL;

-- Set when an administrator creates the user or sets their password; cleared only by the user's own
-- change. Existing accounts keep working as they are.
ALTER TABLE users ADD COLUMN password_change_required BOOLEAN NOT NULL DEFAULT FALSE;

-- Last time the password was set. A web session authenticated before this instant is invalidated.
ALTER TABLE users ADD COLUMN credentials_changed_at TIMESTAMP;

CREATE TABLE password_reset_tokens (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    id_user UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    -- SHA-256 of the token, hex. The token itself is only in the email.
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    expires_at TIMESTAMP NOT NULL,
    used_at TIMESTAMP,
    -- Set when a newer token is issued for the same user, or the password is changed another way.
    invalidated_at TIMESTAMP,
    -- 'self' for the login-page request, otherwise the administrator's username.
    requested_by VARCHAR(100) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_password_reset_tokens_user ON password_reset_tokens (id_user);
