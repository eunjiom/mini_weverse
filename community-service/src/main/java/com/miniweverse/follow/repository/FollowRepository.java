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
     * 아티스트 프로필이 아직 없는 ARTIST 유저도(프로필 생성 API 미구현) 목록에서 빠지지 않도록
     * artist_profiles는 LEFT JOIN한다 — 이 경우 category는 null로 내려간다.
     */
    public List<FollowedArtistResponse> findFollowedArtists(Long followerId) {
        return jdbcTemplate.query(
                """
                SELECT f.artist_id AS artist_id, u.nickname AS nickname, ap.category AS category
                FROM follows f
                JOIN users u ON u.id = f.artist_id AND u.deleted_at IS NULL
                LEFT JOIN artist_profiles ap ON ap.user_id = f.artist_id AND ap.deleted_at IS NULL
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
