package com.miniweverse.post.service;

import com.miniweverse.exception.AuthUserExceptions.InvalidRequestException;
import com.miniweverse.exception.AuthUserExceptions.NotFollowingArtistException;
import com.miniweverse.follow.repository.FollowRepository;
import com.miniweverse.post.entity.Comment;
import com.miniweverse.post.entity.Post;
import com.miniweverse.post.repository.CommentRepository;
import com.miniweverse.post.repository.PostRepository;
import com.miniweverse.user.entity.ArtistProfile;
import com.miniweverse.user.entity.User;
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

    public CommentService(
            CommentRepository commentRepository,
            PostRepository postRepository,
            UserRepository userRepository,
            FollowRepository followRepository
    ) {
        this.commentRepository = commentRepository;
        this.postRepository = postRepository;
        this.userRepository = userRepository;
        this.followRepository = followRepository;
    }

    @Transactional
    public Comment create(Long authorId, Long postId, String content) {
        User author = userRepository.findById(authorId)
                .orElseThrow(() -> new InvalidRequestException("작성자 정보를 찾을 수 없습니다."));
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new InvalidRequestException("게시글을 찾을 수 없습니다."));

        checkFollowAccess(authorId, post);

        return commentRepository.save(Comment.create(post, author, content));
    }

    @Transactional(readOnly = true)
    public List<Comment> getByPost(Long postId, int page, int size) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new InvalidRequestException("게시글을 찾을 수 없습니다."));

        return commentRepository.findByPostOrderByCreatedAtAsc(post, PageRequest.of(page, size));
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
}
