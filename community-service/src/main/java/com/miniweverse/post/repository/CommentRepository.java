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
     * 정렬은 쿼리에 고정(createdAt ASC)하고, Pageable은 페이지/크기(LIMIT/OFFSET)만 적용한다.
     * createdAt이 같은 값일 경우를 대비해 id를 2차 정렬 기준으로 둬서, 오프셋 페이지네이션 시
     * 정렬 순서가 항상 결정적이도록 한다.
     */
    @Query("""
            SELECT c FROM Comment c
            LEFT JOIN FETCH c.author
            JOIN FETCH c.post
            WHERE c.post = :post
            ORDER BY c.createdAt ASC, c.id ASC
            """)
    List<Comment> findByPostOrderByCreatedAtAsc(@Param("post") Post post, Pageable pageable);

    @Query("""
            SELECT c FROM Comment c
            JOIN FETCH c.author
            JOIN FETCH c.post
            WHERE c.author = :author
            ORDER BY c.createdAt DESC
            """)
    List<Comment> findByAuthorOrderByCreatedAtDesc(@Param("author") User author);
}
