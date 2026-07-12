package com.miniweverse.post.service;

import com.miniweverse.exception.AuthUserExceptions.InvalidRequestException;
import com.miniweverse.exception.AuthUserExceptions.NotFollowingArtistException;
import com.miniweverse.follow.repository.FollowRepository;
import com.miniweverse.post.entity.Comment;
import com.miniweverse.post.entity.Post;
import com.miniweverse.post.repository.CommentRepository;
import com.miniweverse.post.repository.PostRepository;
import com.miniweverse.user.entity.User;
import com.miniweverse.user.repository.UserRepository;
import java.util.List;
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
    public List<Comment> getByPost(Long viewerId, Long postId) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new InvalidRequestException("게시글을 찾을 수 없습니다."));

        checkFollowAccess(viewerId, post);

        return commentRepository.findByPostOrderByCreatedAtAsc(post);
    }

    private void checkFollowAccess(Long viewerId, Post post) {
        Long artistUserId = post.getArtistProfile().getUser().getId();
        boolean isArtistSelf = viewerId.equals(artistUserId);
        if (!isArtistSelf && !followRepository.existsByFollowerAndArtist(viewerId, artistUserId)) {
            throw new NotFollowingArtistException();
        }
    }
}
