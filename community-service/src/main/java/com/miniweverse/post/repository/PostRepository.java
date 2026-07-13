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
     * author/artistProfile/ap.user 전부 LEFT JOIN이다 — INNER JOIN이면 작성자나 그 게시판 아티스트가
     * 탈퇴(User/ArtistProfile.deletedAt)했을 때 {@code @SQLRestriction} 때문에, 정작 삭제되지 않은
     * 게시글까지 조회 자체에서 통째로 걸러진다(예: 아티스트가 탈퇴하면 그 게시판의 다른 팬 게시글도
     * 전부 "찾을 수 없음"으로 취급되어 정당한 작성자가 자기 글을 수정/삭제할 수 없게 됨).
     * 목록 조회 쿼리는 artistProfile을 호출부에서 이미 유효성 검증 후 넘겨받아 이 문제가 없다.
     */
    @Query("""
            SELECT p FROM Post p
            LEFT JOIN FETCH p.author
            LEFT JOIN FETCH p.artistProfile ap
            LEFT JOIN FETCH ap.user
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
            LEFT JOIN FETCH p.author
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
