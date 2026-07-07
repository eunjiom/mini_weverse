package com.miniweverse.post.repository;

import com.miniweverse.post.entity.Comment;
import com.miniweverse.post.entity.Post;
import com.miniweverse.user.entity.User;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CommentRepository extends JpaRepository<Comment, Long> {

    /**
     * open-in-view: false라 컨트롤러에서 지연 로딩 필드(author, post)에 접근하면
     * LazyInitializationException이 난다. 응답 DTO 매핑에 필요한 연관관계를 조회 시점에 미리 로딩한다.
     */
    @Query("""
            SELECT c FROM Comment c
            JOIN FETCH c.author
            JOIN FETCH c.post
            WHERE c.post = :post
            ORDER BY c.createdAt ASC
            """)
    List<Comment> findByPostOrderByCreatedAtAsc(@Param("post") Post post);

    @Query("""
            SELECT c FROM Comment c
            JOIN FETCH c.author
            JOIN FETCH c.post
            WHERE c.author = :author
            ORDER BY c.createdAt DESC
            """)
    List<Comment> findByAuthorOrderByCreatedAtDesc(@Param("author") User author);
}
