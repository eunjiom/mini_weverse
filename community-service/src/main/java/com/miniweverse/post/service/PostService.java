package com.miniweverse.post.service;

import com.miniweverse.common.response.CursorPageResponse;
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
    public Post create(Long authorId, Long artistProfileId, BoardType boardType, String content, boolean membersOnly) {
        User author = userRepository.findById(authorId)
                .orElseThrow(() -> new InvalidRequestException("작성자 정보를 찾을 수 없습니다."));
        ArtistProfile artistProfile = artistProfileRepository.findById(artistProfileId)
                .orElseThrow(() -> new InvalidRequestException("아티스트 프로필을 찾을 수 없습니다."));

        if (boardType == BoardType.FEED && !followRepository.existsByFollowerAndArtist(author.getId(), artistProfileId)) {
            throw new InvalidRequestException("팔로우한 아티스트의 피드 게시판에만 글을 작성할 수 있습니다.");
        }

        Post post = postRepository.save(Post.create(author, artistProfile, boardType, content, membersOnly));
        postCacheService.evictPosts(artistProfile, boardType);
        return post;
    }

    @Transactional(readOnly = true)
    public Post getById(Long postId) {
        return postRepository.findByIdWithDetails(postId)
                .orElseThrow(() -> new InvalidRequestException("게시글을 찾을 수 없습니다."));
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

    /**
     * 첫 페이지(cursor=null)는 캐시(최근 {@link PostCacheService#CACHE_CAPACITY}개)에서 서빙하고,
     * 그 이후 페이지(cursor 있음)는 캐시를 거치지 않고 DB에서 직접 조회한다. 무한스크롤 트래픽은
     * 진입 시점(첫 페이지)에 몰리고 그 이후 스크롤은 유저마다 시점이 갈려 분산되므로, 모든 페이지를
     * 캐시와 맞물려 처리하는 복잡도를 들이지 않아도 충분하다고 판단했다.
     */
    @Transactional(readOnly = true)
    public CursorPageResponse<PostResponse> getByArtistAndBoardType(
            Long artistProfileId, BoardType boardType, Long cursor, int size
    ) {
        ArtistProfile artistProfile = artistProfileRepository.findById(artistProfileId)
                .orElseThrow(() -> new InvalidRequestException("아티스트 프로필을 찾을 수 없습니다."));

        if (cursor == null) {
            List<PostResponse> cached = postCacheService.getCachedPosts(artistProfile, boardType);
            return CursorPageResponse.of(cached, size, PostResponse::postId);
        }

        List<PostResponse> fetched = postRepository.findByArtistProfileAndBoardTypeAndCursor(
                        artistProfile, boardType, cursor, PageRequest.of(0, size + 1))
                .stream()
                .map(PostResponse::from)
                .toList();
        return CursorPageResponse.of(fetched, size, PostResponse::postId);
    }
}
