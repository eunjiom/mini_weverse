package com.miniweverse.post.service;

import com.miniweverse.common.response.CursorPageResponse;
import com.miniweverse.exception.AuthUserExceptions.InvalidRequestException;
import com.miniweverse.exception.AuthUserExceptions.MembershipRequiredException;
import com.miniweverse.follow.repository.FollowRepository;
import com.miniweverse.membership.repository.MembershipRepository;
import com.miniweverse.post.dto.PostResponse;
import com.miniweverse.post.entity.Post;
import com.miniweverse.post.enums.BoardType;
import com.miniweverse.post.repository.PostRepository;
import com.miniweverse.user.entity.ArtistProfile;
import com.miniweverse.user.entity.User;
import com.miniweverse.user.enums.MembershipStatus;
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
    private final MembershipRepository membershipRepository;
    private final PostCacheService postCacheService;

    public PostService(
            PostRepository postRepository,
            UserRepository userRepository,
            ArtistProfileRepository artistProfileRepository,
            FollowRepository followRepository,
            MembershipRepository membershipRepository,
            PostCacheService postCacheService
    ) {
        this.postRepository = postRepository;
        this.userRepository = userRepository;
        this.artistProfileRepository = artistProfileRepository;
        this.followRepository = followRepository;
        this.membershipRepository = membershipRepository;
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

    /**
     * 목록과 달리 단건 조회에서 멤버십 전용 글에 접근 권한이 없으면 마스킹된 값을 주지 않고
     * 바로 403(MEMBERSHIP_REQUIRED)으로 막는다 — 목록에서 이미 잠긴 글인 걸 보여준 뒤라, 단건
     * 조회는 "그래서 볼 수 있냐 없냐"를 명확히 알려주는 편이 낫다고 판단했다.
     */
    @Transactional(readOnly = true)
    public PostResponse getById(Long viewerId, Long postId) {
        Post post = postRepository.findByIdWithDetails(postId)
                .orElseThrow(() -> new InvalidRequestException("게시글을 찾을 수 없습니다."));
        if (post.isMembersOnly() && !hasFullAccess(viewerId, post.getArtistProfile())) {
            throw new MembershipRequiredException();
        }
        return PostResponse.from(post);
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
            Long viewerId, Long artistProfileId, BoardType boardType, Long cursor, int size
    ) {
        ArtistProfile artistProfile = artistProfileRepository.findById(artistProfileId)
                .orElseThrow(() -> new InvalidRequestException("아티스트 프로필을 찾을 수 없습니다."));

        boolean hasFullAccess = hasFullAccess(viewerId, artistProfile);

        List<PostResponse> fetched;
        if (cursor == null) {
            fetched = postCacheService.getCachedPosts(artistProfile, boardType);
        } else {
            fetched = postRepository.findByArtistProfileAndBoardTypeAndCursor(
                            artistProfile, boardType, cursor, PageRequest.of(0, size + 1))
                    .stream()
                    .map(PostResponse::from)
                    .toList();
        }

        List<PostResponse> masked = fetched.stream()
                .map(response -> response.membersOnly() && !hasFullAccess ? response.mask() : response)
                .toList();
        return CursorPageResponse.of(masked, size, PostResponse::postId);
    }

    /**
     * 멤버십 전용 글을 잠금 없이 볼 수 있는지 — 본인(아티스트) 또는 활성 구독자면 true.
     * artistProfile이 null(작성 당시 아티스트가 이미 탈퇴 등)이면 판단 불가하므로 접근을 허용하지 않는다.
     */
    private boolean hasFullAccess(Long viewerId, ArtistProfile artistProfile) {
        if (viewerId == null || artistProfile == null) {
            return false;
        }
        User artistUser = artistProfile.getUser();
        if (artistUser != null && Objects.equals(viewerId, artistUser.getId())) {
            return true;
        }
        return membershipRepository.existsBySubscriberIdAndArtistAndStatus(viewerId, artistProfile, MembershipStatus.ACTIVE);
    }
}
