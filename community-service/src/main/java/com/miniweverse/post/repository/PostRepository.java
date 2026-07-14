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
     * 수정/삭제 시 본인 확인 및 응답 매핑에 필요한 연관관계까지 미리 로딩한다.
     * author/artistProfile/ap.user 전부 LEFT JOIN이다 — INNER JOIN이면 작성자나 그 게시판 아티스트가
     * 탈퇴(User/ArtistProfile.deletedAt)했을 때 {@code @SQLRestriction} 때문에, 정작 삭제되지 않은
     * 게시글까지 조회 자체에서 통째로 걸러진다(예: 아티스트가 탈퇴하면 그 게시판의 다른 팬 게시글도
     * 전부 "찾을 수 없음"으로 취급되어 정당한 작성자가 자기 글을 수정/삭제할 수 없게 됨).
     * 아래 목록 조회 쿼리들도 ap.user 탈퇴 시 같은 문제가 있어 동일하게 LEFT JOIN으로 맞춘다
     * (artistProfile 자체는 호출부에서 이미 유효성 검증하지만, 그 소유주 User의 탈퇴 여부까지는
     * 별개 문제라 검증되지 않는다).
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
     * ID 기준 커서 페이지네이션 — IDENTITY PK가 삽입 순서를 그대로 반영해 createdAt 정렬과 사실상
     * 동일하면서 복합 인덱스 없이 PK만으로 처리 가능하다. cursor가 null이면 최신 목록(캐시 채우기 등)을
     * 조회하고, 있으면 그 id보다 작은(더 오래된) 게시글을 가져온다. Pageable은 LIMIT(개수 제한)에만 쓴다.
     */
    @Query("""
            SELECT p FROM Post p
            LEFT JOIN FETCH p.author
            LEFT JOIN FETCH p.artistProfile ap
            LEFT JOIN FETCH ap.user
            WHERE p.artistProfile = :artistProfile AND p.boardType = :boardType
            AND (:cursor IS NULL OR p.id < :cursor)
            ORDER BY p.id DESC
            """)
    List<Post> findByArtistProfileAndBoardTypeAndCursor(
            @Param("artistProfile") ArtistProfile artistProfile,
            @Param("boardType") BoardType boardType,
            @Param("cursor") Long cursor,
            Pageable pageable
    );

    /**
     * 유저 프로필의 "작성한 글" 목록 — ID 기준 커서 페이지네이션(다른 목록 조회와 동일한 방식).
     */
    @Query("""
            SELECT p FROM Post p
            LEFT JOIN FETCH p.author
            LEFT JOIN FETCH p.artistProfile ap
            LEFT JOIN FETCH ap.user
            WHERE p.author = :author
            AND (:cursor IS NULL OR p.id < :cursor)
            ORDER BY p.id DESC
            """)
    List<Post> findByAuthorAndCursor(@Param("author") User author, @Param("cursor") Long cursor, Pageable pageable);
}
