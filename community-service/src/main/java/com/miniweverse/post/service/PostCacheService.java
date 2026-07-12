package com.miniweverse.post.service;

import com.miniweverse.post.dto.PostResponse;
import com.miniweverse.post.enums.BoardType;
import com.miniweverse.post.repository.PostRepository;
import com.miniweverse.user.entity.ArtistProfile;
import java.util.List;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

/**
 * 게시판(artistId + boardType) 단위로 최근 게시글 최대 {@link #CACHE_CAPACITY}개를 캐시한다
 * (Push 모델: 글 작성 시 갱신). 페이지네이션은 이 캐시된 목록 안에서 잘라 제공하고,
 * 캐시 범위를 벗어나는 페이지는 PostService가 DB에서 직접 조회한다.
 * 팔로우 여부 체크는 이 캐시와 무관하게 PostService에서 매번 확인한다.
 */
@Service
public class PostCacheService {

    public static final int CACHE_CAPACITY = 100;

    private final PostRepository postRepository;

    public PostCacheService(PostRepository postRepository) {
        this.postRepository = postRepository;
    }

    @Cacheable(value = "posts", key = "#p0.id + ':' + #p1")
    public List<PostResponse> getCachedPosts(ArtistProfile artistProfile, BoardType boardType) {
        return postRepository.findByArtistProfileAndBoardTypeOrderByCreatedAtDesc(
                        artistProfile, boardType, PageRequest.of(0, CACHE_CAPACITY))
                .stream()
                .map(PostResponse::from)
                .toList();
    }

    @CacheEvict(value = "posts", key = "#p0.id + ':' + #p1")
    public void evictPosts(ArtistProfile artistProfile, BoardType boardType) {
    }
}
