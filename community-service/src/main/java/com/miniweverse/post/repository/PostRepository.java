package com.miniweverse.post.repository;

import com.miniweverse.post.entity.Post;
import com.miniweverse.post.enums.BoardType;
import com.miniweverse.user.entity.ArtistProfile;
import com.miniweverse.user.entity.User;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PostRepository extends JpaRepository<Post, Long> {

    /**
     * open-in-view: false라 컨트롤러에서 지연 로딩 필드(author, artistProfile.user)에 접근하면
     * LazyInitializationException이 난다. 응답 DTO 매핑에 필요한 연관관계를 조회 시점에 미리 로딩한다.
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
            @Param("boardType") BoardType boardType
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
