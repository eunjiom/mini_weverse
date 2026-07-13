package com.miniweverse.post.dto;

import com.miniweverse.post.entity.Comment;
import com.miniweverse.user.entity.User;
import java.time.LocalDateTime;

public record CommentResponse(
        Long commentId,
        Long postId,
        Long authorId,
        String authorNickname,
        String content,
        LocalDateTime createdAt
) {
    private static final String WITHDRAWN_AUTHOR_NICKNAME = "탈퇴한 사용자";

    public static CommentResponse from(Comment comment) {
        User author = comment.getAuthor();
        return new CommentResponse(
                comment.getId(),
                comment.getPost().getId(),
                author != null ? author.getId() : null,
                author != null ? author.getNickname() : WITHDRAWN_AUTHOR_NICKNAME,
                comment.getContent(),
                comment.getCreatedAt()
        );
    }
}
