package com.miniweverse.post.repository;

import com.miniweverse.post.entity.Post;
import com.miniweverse.post.enums.BoardType;
import com.miniweverse.user.entity.ArtistProfile;
import com.miniweverse.user.entity.User;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PostRepository extends JpaRepository<Post, Long> {

    /**
     * 수정/삭제 시 본인 확인 및 응답 매핑에 필요한 연관관계까지 미리 로딩한다 (목록 조회 쿼리와 동일한 이유).
     */
    @Query("""
            SELECT p FROM Post p
            JOIN FETCH p.author
            JOIN FETCH p.artistProfile ap
            JOIN FETCH ap.user
            WHERE p.id = :id
            """)
    Optional<Post> findByIdWithDetails(@Param("id") Long id);

    /**
     * open-in-view: false라 컨트롤러에서 지연 로딩 필드(author, artistProfile.user)에 접근하면
     * LazyInitializationException이 난다. 응답 DTO 매핑에 필요한 연관관계를 조회 시점에 미리 로딩한다.
     * 정렬은 쿼리에 고정(createdAt DESC)하고, Pageable은 페이지/크기(LIMIT/OFFSET)만 적용한다.
     */
    @Query("""
            SELECT p FROM Post p
            JOIN FETCH p.author
            JOIN FETCH p.artistProfile ap
            JOIN FETCH ap.user
            WHERE p.artistProfile = :artistProfile AND p.boardType = :boardType
            ORDER BY p.createdAt DESC
            """)
    List<Post> findByArtistProfileAndBoardTypeOrderByCreatedAtDesc(
            @Param("artistProfile") ArtistProfile artistProfile,
            @Param("boardType") BoardType boardType,
            Pageable pageable
    );

    @Query("""
            SELECT p FROM Post p
            JOIN FETCH p.author
            JOIN FETCH p.artistProfile ap
            JOIN FETCH ap.user
            WHERE p.author = :author
            ORDER BY p.createdAt DESC
            """)
    List<Post> findByAuthorOrderByCreatedAtDesc(@Param("author") User author);
}
