package com.miniweverse.post.service;

import com.miniweverse.exception.AuthUserExceptions.InvalidRequestException;
import com.miniweverse.follow.repository.FollowRepository;
import com.miniweverse.post.dto.PostResponse;
import com.miniweverse.post.entity.Post;
import com.miniweverse.post.enums.BoardType;
import com.miniweverse.post.repository.PostRepository;
import com.miniweverse.user.entity.ArtistProfile;
import com.miniweverse.user.entity.User;
import com.miniweverse.user.repository.ArtistProfileRepository;
import com.miniweverse.user.repository.UserRepository;
import java.util.List;
import java.util.Objects;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PostService {

    private final PostRepository postRepository;
    private final UserRepository userRepository;
    private final ArtistProfileRepository artistProfileRepository;
    private final FollowRepository followRepository;
    private final PostCacheService postCacheService;

    public PostService(
            PostRepository postRepository,
            UserRepository userRepository,
            ArtistProfileRepository artistProfileRepository,
            FollowRepository followRepository,
            PostCacheService postCacheService
    ) {
        this.postRepository = postRepository;
        this.userRepository = userRepository;
        this.artistProfileRepository = artistProfileRepository;
        this.followRepository = followRepository;
        this.postCacheService = postCacheService;
    }

    @Transactional
    public Post create(Long authorId, Long artistProfileId, BoardType boardType, String content) {
        User author = userRepository.findById(authorId)
                .orElseThrow(() -> new InvalidRequestException("작성자 정보를 찾을 수 없습니다."));
        ArtistProfile artistProfile = artistProfileRepository.findById(artistProfileId)
                .orElseThrow(() -> new InvalidRequestException("아티스트 프로필을 찾을 수 없습니다."));

        if (boardType == BoardType.FEED && !followRepository.existsByFollowerAndArtist(author.getId(), artistProfileId)) {
            throw new InvalidRequestException("팔로우한 아티스트의 피드 게시판에만 글을 작성할 수 있습니다.");
        }

        Post post = postRepository.save(Post.create(author, artistProfile, boardType, content));
        postCacheService.evictPosts(artistProfile, boardType);
        return post;
    }

    @Transactional
    public Post update(Long postId, Long requesterId, String content) {
        Post post = postRepository.findByIdWithDetails(postId)
                .orElseThrow(() -> new InvalidRequestException("게시글을 찾을 수 없습니다."));
        if (post.getAuthor() == null || !Objects.equals(post.getAuthor().getId(), requesterId)) {
            throw new InvalidRequestException("본인 게시글만 수정할 수 있습니다.");
        }
        post.updateContent(content);
        postCacheService.evictPosts(post.getArtistProfile(), post.getBoardType());
        return post;
    }

    @Transactional
    public void delete(Long postId, Long requesterId) {
        Post post = postRepository.findByIdWithDetails(postId)
                .orElseThrow(() -> new InvalidRequestException("게시글을 찾을 수 없습니다."));
        if (post.getAuthor() == null || !Objects.equals(post.getAuthor().getId(), requesterId)) {
            throw new InvalidRequestException("본인 게시글만 삭제할 수 있습니다.");
        }
        post.delete();
        postCacheService.evictPosts(post.getArtistProfile(), post.getBoardType());
    }

    @Transactional(readOnly = true)
    public List<PostResponse> getByArtistAndBoardType(
            Long artistProfileId, BoardType boardType, int page, int size
    ) {
        ArtistProfile artistProfile = artistProfileRepository.findById(artistProfileId)
                .orElseThrow(() -> new InvalidRequestException("아티스트 프로필을 찾을 수 없습니다."));

        // page에는 상한이 없어 int로 계산하면 큰 값에서 오버플로가 날 수 있으므로 long으로 계산한다.
        long offset = (long) page * size;
        if (offset >= 0 && offset + size <= PostCacheService.CACHE_CAPACITY) {
            List<PostResponse> cached = postCacheService.getCachedPosts(artistProfile, boardType);
            int fromIndex = Math.min((int) offset, cached.size());
            int toIndex = Math.min((int) (offset + size), cached.size());
            return cached.subList(fromIndex, toIndex);
        }

        // JPA의 Query.setFirstResult(int)는 offset이 Integer.MAX_VALUE를 넘으면 예외를 던진다.
        // 그 범위를 벗어나는 페이지는 실제로 존재할 수 없는 데이터이므로 조회 없이 빈 목록을 반환한다.
        if (offset > Integer.MAX_VALUE) {
            return List.of();
        }

        // 캐시 범위(최근 CACHE_CAPACITY개)를 벗어난 페이지는 캐시를 거치지 않고 DB에서 직접 조회한다.
        return postRepository.findByArtistProfileAndBoardTypeOrderByCreatedAtDesc(
                        artistProfile, boardType, PageRequest.of(page, size))
                .stream()
                .map(PostResponse::from)
                .toList();
    }
}
