package com.miniweverse.follow.repository;

import com.miniweverse.follow.dto.FollowedArtistResponse;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class FollowRepository {

    private final JdbcTemplate jdbcTemplate;

    public FollowRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * follows.artist_id는 이제 artist_profiles.id를 직접 참조하는 FK라, "프로필 없는 ARTIST
     * 유저를 팔로우한 경우"는 구조적으로 존재할 수 없다 — artist_profiles는 INNER JOIN으로 충분하다.
     */
    public List<FollowedArtistResponse> findFollowedArtists(Long followerId) {
        return jdbcTemplate.query(
                """
                SELECT f.artist_id AS artist_id, u.nickname AS nickname, ap.category AS category
                FROM follows f
                JOIN artist_profiles ap ON ap.id = f.artist_id AND ap.deleted_at IS NULL
                JOIN users u ON u.id = ap.user_id AND u.deleted_at IS NULL
                WHERE f.follower_id = ?
                ORDER BY f.created_at DESC
                """,
                (rs, rowNum) -> new FollowedArtistResponse(
                        rs.getLong("artist_id"),
                        rs.getString("nickname"),
                        rs.getString("category")
                ),
                followerId
        );
    }

    /**
     * userId가 아티스트로서 가진 팔로워 수. ArtistProfile이 없는 유저(팬)는 0이 나온다.
     * 탈퇴한 팔로워(users.deleted_at)는 findFollowedArtists의 필터 조건과 맞추기 위해 제외한다.
     */
    public long countFollowers(Long userId) {
        Long count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*) FROM follows f
                JOIN artist_profiles ap ON ap.id = f.artist_id AND ap.deleted_at IS NULL
                JOIN users u ON u.id = f.follower_id AND u.deleted_at IS NULL
                WHERE ap.user_id = ?
                """,
                Long.class, userId
        );
        return count != null ? count : 0L;
    }

    /**
     * findFollowedArtists와 동일한 조건(탈퇴한 아티스트/유저 제외)으로 세야, 팔로잉 수와
     * 실제 목록에 보이는 항목 수가 어긋나지 않는다.
     */
    public long countFollowing(Long followerId) {
        Long count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*) FROM follows f
                JOIN artist_profiles ap ON ap.id = f.artist_id AND ap.deleted_at IS NULL
                JOIN users u ON u.id = ap.user_id AND u.deleted_at IS NULL
                WHERE f.follower_id = ?
                """,
                Long.class, followerId
        );
        return count != null ? count : 0L;
    }

    /**
     * artistProfileId를 팔로우 중인 팬 유저 id 목록 — 아티스트가 새 글을 올렸을 때 알림을
     * fan-out할 대상을 정할 때 쓴다. 탈퇴한 팔로워는 제외한다(다른 조회들과 동일 조건).
     */
    public List<Long> findFollowerIds(Long artistProfileId) {
        return jdbcTemplate.query(
                """
                SELECT f.follower_id AS follower_id
                FROM follows f
                JOIN users u ON u.id = f.follower_id AND u.deleted_at IS NULL
                WHERE f.artist_id = ?
                """,
                (rs, rowNum) -> rs.getLong("follower_id"),
                artistProfileId
        );
    }

    public boolean existsByFollowerAndArtist(Long followerId, Long artistId) {
        Boolean exists = jdbcTemplate.queryForObject(
                "SELECT EXISTS(SELECT 1 FROM follows WHERE follower_id = ? AND artist_id = ?)",
                Boolean.class, followerId, artistId
        );
        return Boolean.TRUE.equals(exists);
    }

    public void insert(Long followerId, Long artistId) {
        LocalDateTime now = LocalDateTime.now();
        jdbcTemplate.update(
                "INSERT INTO follows (follower_id, artist_id, created_at, updated_at) VALUES (?, ?, ?, ?)",
                followerId, artistId, now, now
        );
    }

    public void delete(Long followerId, Long artistId) {
        jdbcTemplate.update(
                "DELETE FROM follows WHERE follower_id = ? AND artist_id = ?",
                followerId, artistId
        );
    }
}
