package com.miniweverse.post.service;

import com.miniweverse.post.dto.PostResponse;
import com.miniweverse.post.enums.BoardType;
import com.miniweverse.post.repository.PostRepository;
import com.miniweverse.user.entity.ArtistProfile;
import java.util.List;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

/**
 * 게시판(artistId + boardType) 단위로 게시글 목록을 캐시한다 (Push 모델: 글 작성 시 갱신).
 * 팔로우 여부 체크는 이 캐시와 무관하게 PostService에서 매번 확인한다.
 * JPA 엔티티(지연 로딩 프록시 포함)는 직렬화가 안 되므로 DTO로 변환한 뒤 캐시에 담는다.
 */
@Service
public class PostCacheService {

    private final PostRepository postRepository;

    public PostCacheService(PostRepository postRepository) {
        this.postRepository = postRepository;
    }

    @Cacheable(value = "posts", key = "#p0.id + ':' + #p1")
    public List<PostResponse> getPosts(ArtistProfile artistProfile, BoardType boardType) {
        return postRepository.findByArtistProfileAndBoardTypeOrderByCreatedAtDesc(artistProfile, boardType).stream()
                .map(PostResponse::from)
                .toList();
    }

    @CacheEvict(value = "posts", key = "#p0.id + ':' + #p1")
    public void evictPosts(ArtistProfile artistProfile, BoardType boardType) {
    }
}
