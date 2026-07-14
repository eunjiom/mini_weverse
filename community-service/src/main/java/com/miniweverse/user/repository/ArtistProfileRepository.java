package com.miniweverse.user.repository;

import com.miniweverse.user.entity.ArtistProfile;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ArtistProfileRepository extends JpaRepository<ArtistProfile, Long> {

    /**
     * ID 기준 커서 페이지네이션(게시글/댓글과 동일한 방식) — cursor보다 작은(더 이전에 생성된)
     * 프로필을 채널명 대소문자 무시 부분검색으로 가져온다.
     */
    @Query("""
            SELECT ap FROM ArtistProfile ap
            WHERE LOWER(ap.channelName) LIKE LOWER(CONCAT('%', :channelName, '%'))
            AND (:cursor IS NULL OR ap.id < :cursor)
            ORDER BY ap.id DESC
            """)
    List<ArtistProfile> findByChannelNameAndCursor(
            @Param("channelName") String channelName,
            @Param("cursor") Long cursor,
            Pageable pageable
    );
}
