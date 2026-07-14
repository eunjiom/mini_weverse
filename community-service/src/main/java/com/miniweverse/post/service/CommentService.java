package com.miniweverse.post.service;

import com.miniweverse.common.response.CursorPageResponse;
import com.miniweverse.exception.AuthUserExceptions.InvalidRequestException;
import com.miniweverse.exception.AuthUserExceptions.MembershipRequiredException;
import com.miniweverse.exception.AuthUserExceptions.NotFollowingArtistException;
import com.miniweverse.follow.repository.FollowRepository;
import com.miniweverse.membership.repository.MembershipRepository;
import com.miniweverse.post.dto.CommentResponse;
import com.miniweverse.post.entity.Comment;
import com.miniweverse.post.entity.Post;
import com.miniweverse.post.repository.CommentRepository;
import com.miniweverse.post.repository.PostRepository;
import com.miniweverse.user.entity.ArtistProfile;
import com.miniweverse.user.entity.User;
import com.miniweverse.user.enums.MembershipStatus;
import com.miniweverse.user.repository.UserRepository;
import java.util.List;
import java.util.Objects;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CommentService {

    private final CommentRepository commentRepository;
    private final PostRepository postRepository;
    private final UserRepository userRepository;
    private final FollowRepository followRepository;
    private final MembershipRepository membershipRepository;

    public CommentService(
            CommentRepository commentRepository,
            PostRepository postRepository,
            UserRepository userRepository,
            FollowRepository followRepository,
            MembershipRepository membershipRepository
    ) {
        this.commentRepository = commentRepository;
        this.postRepository = postRepository;
        this.userRepository = userRepository;
        this.followRepository = followRepository;
        this.membershipRepository = membershipRepository;
    }

    @Transactional
    public Comment create(Long authorId, Long postId, String content) {
        User author = userRepository.findById(authorId)
                .orElseThrow(() -> new InvalidRequestException("작성자 정보를 찾을 수 없습니다."));
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new InvalidRequestException("게시글을 찾을 수 없습니다."));

        checkFollowAccess(authorId, post);
        checkMembershipAccess(authorId, post);

        return commentRepository.save(Comment.create(post, author, content));
    }

    @Transactional(readOnly = true)
    public CursorPageResponse<CommentResponse> getByPost(Long viewerId, Long postId, Long cursor, int size) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new InvalidRequestException("게시글을 찾을 수 없습니다."));

        checkMembershipAccess(viewerId, post);

        List<CommentResponse> fetched = commentRepository.findByPostAndCursor(post, cursor, PageRequest.of(0, size + 1))
                .stream()
                .map(CommentResponse::from)
                .toList();
        return CursorPageResponse.of(fetched, size, CommentResponse::commentId);
    }

    /**
     * 유저 프로필의 "작성한 댓글" 목록 — 댓글 텍스트 자체는 원글 내용을 노출하지 않아 그대로 보여주지만,
     * postId는 남의 프로필에서 볼 때 멤버십 전용 글이면 숨긴다(그 postId를 통해 "이 사람이 이 잠긴
     * 글에 댓글을 달았다"는 사실이 구독 여부와 무관하게 드러나는 걸 막기 위함). 본인 프로필이면 항상 그대로 보인다.
     */
    @Transactional(readOnly = true)
    public CursorPageResponse<CommentResponse> getByAuthor(Long viewerId, Long authorId, Long cursor, int size) {
        User author = userRepository.findById(authorId)
                .orElseThrow(() -> new InvalidRequestException("유저 정보를 찾을 수 없습니다."));

        boolean isOwnProfile = Objects.equals(viewerId, authorId);
        List<CommentResponse> fetched = commentRepository.findByAuthorAndCursor(author, cursor, PageRequest.of(0, size + 1))
                .stream()
                .map(comment -> {
                    CommentResponse response = CommentResponse.from(comment);
                    boolean postLocked = comment.getPost() != null && comment.getPost().isMembersOnly();
                    return postLocked && !isOwnProfile ? response.withoutPostId() : response;
                })
                .toList();
        return CursorPageResponse.of(fetched, size, CommentResponse::commentId);
    }

    @Transactional
    public Comment update(Long commentId, Long requesterId, String content) {
        Comment comment = commentRepository.findByIdWithDetails(commentId)
                .orElseThrow(() -> new InvalidRequestException("댓글을 찾을 수 없습니다."));
        if (comment.getAuthor() == null || !Objects.equals(comment.getAuthor().getId(), requesterId)) {
            throw new InvalidRequestException("본인 댓글만 수정할 수 있습니다.");
        }
        comment.updateContent(content);
        return comment;
    }

    @Transactional
    public void delete(Long commentId, Long requesterId) {
        Comment comment = commentRepository.findByIdWithDetails(commentId)
                .orElseThrow(() -> new InvalidRequestException("댓글을 찾을 수 없습니다."));
        if (comment.getAuthor() == null || !Objects.equals(comment.getAuthor().getId(), requesterId)) {
            throw new InvalidRequestException("본인 댓글만 삭제할 수 있습니다.");
        }
        comment.delete();
    }

    private void checkFollowAccess(Long viewerId, Post post) {
        // artistProfile 또는 그 소유주 User가 탈퇴했으면(@NotFound(IGNORE)로 null 처리됨) 아티스트를
        // 특정할 수 없으므로, 팔로우 여부를 판단하지 못하고 명확한 에러로 막는다(NPE 대신).
        ArtistProfile artistProfile = post.getArtistProfile();
        User artistUser = artistProfile != null ? artistProfile.getUser() : null;
        if (artistUser == null) {
            throw new InvalidRequestException("아티스트 정보를 찾을 수 없습니다.");
        }
        boolean isArtistSelf = Objects.equals(viewerId, artistUser.getId());
        if (!isArtistSelf && !followRepository.existsByFollowerAndArtist(viewerId, artistProfile.getId())) {
            throw new NotFollowingArtistException();
        }
    }

    /**
     * 게시글이 멤버십 전용이면, 그 댓글도 본문을 볼 수 있는 사람(본인 또는 활성 구독자)만
     * 보거나 달 수 있다 — 본문은 잠겨있는데 댓글 토론만 공개되는 건 앞뒤가 안 맞는다는 판단.
     */
    private void checkMembershipAccess(Long viewerId, Post post) {
        if (!post.isMembersOnly()) {
            return;
        }
        ArtistProfile artistProfile = post.getArtistProfile();
        User artistUser = artistProfile != null ? artistProfile.getUser() : null;
        boolean isArtistSelf = artistUser != null && Objects.equals(viewerId, artistUser.getId());
        boolean isSubscriber = viewerId != null && artistProfile != null
                && membershipRepository.existsBySubscriberIdAndArtistAndStatus(viewerId, artistProfile, MembershipStatus.ACTIVE);
        if (!isArtistSelf && !isSubscriber) {
            throw new MembershipRequiredException();
        }
    }
}
