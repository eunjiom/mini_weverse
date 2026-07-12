package com.miniweverse.follow.repository;

import java.time.LocalDateTime;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class FollowRepository {

    private final JdbcTemplate jdbcTemplate;

    public FollowRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
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
