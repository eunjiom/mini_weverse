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
-- 자기팔로우 방지 CHECK(follower_id <> artist_id)는 의도적으로 없다 — artist_id가 ArtistProfile PK로
-- 바뀐 뒤로는 follower_id(User PK)와 서로 다른 시퀀스라 값이 우연히 같아지거나 달라질 수 있어,
-- 단순 컬럼 비교로는 자기팔로우 여부를 정확히 판단할 수 없다(CHECK는 다른 테이블을 참조 못 함).
-- 자기팔로우 방지는 Follow.create()가 artistProfile.getUser().getId()까지 확인해서 애플리케이션
-- 레벨에서만 정확하게 처리한다.
CREATE TABLE IF NOT EXISTS follows (
    follower_id BIGINT NOT NULL REFERENCES users (id),
    artist_id BIGINT NOT NULL REFERENCES artist_profiles (id),
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (follower_id, artist_id)
);
