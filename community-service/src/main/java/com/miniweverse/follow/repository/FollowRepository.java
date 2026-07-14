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
     */
    public long countFollowers(Long userId) {
        Long count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*) FROM follows f
                JOIN artist_profiles ap ON ap.id = f.artist_id AND ap.deleted_at IS NULL
                WHERE ap.user_id = ?
                """,
                Long.class, userId
        );
        return count != null ? count : 0L;
    }

    public long countFollowing(Long followerId) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM follows WHERE follower_id = ?",
                Long.class, followerId
        );
        return count != null ? count : 0L;
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
