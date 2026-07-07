package com.miniweverse.post.repository;

import com.miniweverse.post.entity.Post;
import com.miniweverse.post.enums.BoardType;
import com.miniweverse.user.entity.ArtistProfile;
import com.miniweverse.user.entity.User;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PostRepository extends JpaRepository<Post, Long> {

    List<Post> findByArtistProfileAndBoardTypeOrderByCreatedAtDesc(ArtistProfile artistProfile, BoardType boardType);

    List<Post> findByAuthorOrderByCreatedAtDesc(User author);
}
