package com.miniweverse.post.dto;

import com.miniweverse.post.entity.Post;
import com.miniweverse.post.enums.BoardType;
import com.miniweverse.user.entity.User;
import java.time.LocalDateTime;

public record PostResponse(
        Long postId,
        Long authorId,
        String authorNickname,
        Long artistId,
        BoardType boardType,
        String content,
        LocalDateTime createdAt
) {
    private static final String WITHDRAWN_AUTHOR_NICKNAME = "탈퇴한 사용자";

    public static PostResponse from(Post post) {
        User author = post.getAuthor();
        return new PostResponse(
                post.getId(),
                author != null ? author.getId() : null,
                author != null ? author.getNickname() : WITHDRAWN_AUTHOR_NICKNAME,
                post.getArtistProfile().getUser().getId(),
                post.getBoardType(),
                post.getContent(),
                post.getCreatedAt()
        );
    }
}
