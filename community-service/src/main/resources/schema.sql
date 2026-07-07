CREATE UNIQUE INDEX IF NOT EXISTS uk_users_email_active
    ON users (email)
    WHERE deleted_at IS NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uk_users_provider_id
    ON users (provider_id)
    WHERE provider_id IS NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uk_artist_profiles_user_id
    ON artist_profiles (user_id)
    WHERE deleted_at IS NULL;
