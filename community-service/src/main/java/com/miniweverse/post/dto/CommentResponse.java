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

    /**
     * 멤버십 전용 글에 달린 댓글을 남의 프로필에서 볼 때 postId를 숨긴다 — postId가 그대로 노출되면
     * "이 유저가 이 postId(잠긴 글)에 댓글을 달았다"는 사실이 구독 여부와 무관하게 드러나기 때문이다.
     */
    public CommentResponse withoutPostId() {
        return new CommentResponse(commentId, null, authorId, authorNickname, content, createdAt);
    }
}
