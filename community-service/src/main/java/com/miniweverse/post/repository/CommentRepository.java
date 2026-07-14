package com.miniweverse.post.repository;

import com.miniweverse.post.entity.Comment;
import com.miniweverse.post.entity.Post;
import com.miniweverse.user.entity.User;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CommentRepository extends JpaRepository<Comment, Long> {

    /**
     * 수정/삭제 시 본인 확인 및 응답 매핑에 필요한 연관관계까지 미리 로딩한다.
     * author는 LEFT JOIN이다 — INNER JOIN이면 작성자가 탈퇴(User.deletedAt)한 댓글이
     * User의 {@code @SQLRestriction} 때문에 조회 자체에서 통째로 걸러진다.
     */
    @Query("""
            SELECT c FROM Comment c
            LEFT JOIN FETCH c.author
            JOIN FETCH c.post
            WHERE c.id = :id
            """)
    Optional<Comment> findByIdWithDetails(@Param("id") Long id);

    /**
     * open-in-view: false라 컨트롤러에서 지연 로딩 필드(author, post)에 접근하면
     * LazyInitializationException이 난다. 응답 DTO 매핑에 필요한 연관관계를 조회 시점에 미리 로딩한다.
     * ID 기준 커서 페이지네이션 — 댓글은 오래된 순으로 보여주다가 스크롤하면 그 이후(더 나중에 달린)
     * 댓글을 이어서 불러오는 방향이라, cursor보다 큰(더 나중 id) 댓글을 오름차순으로 가져온다.
     */
    @Query("""
            SELECT c FROM Comment c
            LEFT JOIN FETCH c.author
            JOIN FETCH c.post
            WHERE c.post = :post
            AND (:cursor IS NULL OR c.id > :cursor)
            ORDER BY c.id ASC
            """)
    List<Comment> findByPostAndCursor(@Param("post") Post post, @Param("cursor") Long cursor, Pageable pageable);

    /**
     * 유저 프로필의 "작성한 댓글" 목록 — ID 기준 커서 페이지네이션.
     */
    @Query("""
            SELECT c FROM Comment c
            LEFT JOIN FETCH c.author
            JOIN FETCH c.post
            WHERE c.author = :author
            AND (:cursor IS NULL OR c.id < :cursor)
            ORDER BY c.id DESC
            """)
    List<Comment> findByAuthorAndCursor(@Param("author") User author, @Param("cursor") Long cursor, Pageable pageable);
}
