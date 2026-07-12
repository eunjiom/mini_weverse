CREATE UNIQUE INDEX IF NOT EXISTS uk_users_email_active
    ON users (email)
    WHERE deleted_at IS NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uk_users_provider_id
    ON users (provider_id)
    WHERE provider_id IS NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uk_artist_profiles_user_id
    ON artist_profiles (user_id)
    WHERE deleted_at IS NULL;

-- Follow는 JPA 엔티티로 관리하지 않는 순수 조인 테이블이라 Hibernate ddl-auto가 만들어주지 않는다.
-- (follower_id, artist_id) 자체가 이 관계의 유일한 식별자라 별도 surrogate id 없이 복합 PK로 둔다.
CREATE TABLE IF NOT EXISTS follows (
    follower_id BIGINT NOT NULL REFERENCES users (id),
    artist_id BIGINT NOT NULL REFERENCES users (id),
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (follower_id, artist_id),
    CONSTRAINT chk_follows_no_self_follow CHECK (follower_id <> artist_id)
);
