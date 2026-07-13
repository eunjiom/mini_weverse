package com.miniweverse.post.dto;

import com.miniweverse.post.entity.Post;
import com.miniweverse.post.enums.BoardType;
import com.miniweverse.user.entity.ArtistProfile;
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
        Long artistId = extractArtistId(post.getArtistProfile());
        return new PostResponse(
                post.getId(),
                author != null ? author.getId() : null,
                author != null ? author.getNickname() : WITHDRAWN_AUTHOR_NICKNAME,
                artistId,
                post.getBoardType(),
                post.getContent(),
                post.getCreatedAt()
        );
    }

    private static Long extractArtistId(ArtistProfile artistProfile) {
        if (artistProfile == null || artistProfile.getUser() == null) {
            return null;
        }
        return artistProfile.getUser().getId();
    }
}
