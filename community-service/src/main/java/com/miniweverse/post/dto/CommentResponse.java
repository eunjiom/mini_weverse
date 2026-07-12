package com.miniweverse.post.dto;

import com.miniweverse.post.entity.Comment;
import java.time.LocalDateTime;

public record CommentResponse(
        Long commentId,
        Long postId,
        Long authorId,
        String authorNickname,
        String content,
        LocalDateTime createdAt
) {
    public static CommentResponse from(Comment comment) {
        return new CommentResponse(
                comment.getId(),
                comment.getPost().getId(),
                comment.getAuthor().getId(),
                comment.getAuthor().getNickname(),
                comment.getContent(),
                comment.getCreatedAt()
        );
    }
}
