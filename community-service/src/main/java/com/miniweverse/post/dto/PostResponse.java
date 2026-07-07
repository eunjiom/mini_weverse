package com.miniweverse.post.dto;

import com.miniweverse.post.entity.Post;
import com.miniweverse.post.enums.BoardType;
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
    public static PostResponse from(Post post) {
        return new PostResponse(
                post.getId(),
                post.getAuthor().getId(),
                post.getAuthor().getNickname(),
                post.getArtistProfile().getUser().getId(),
                post.getBoardType(),
                post.getContent(),
                post.getCreatedAt()
        );
    }
}
