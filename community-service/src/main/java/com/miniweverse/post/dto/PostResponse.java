package com.miniweverse.post.dto;

import com.miniweverse.post.entity.Post;
import com.miniweverse.post.enums.BoardType;
import com.miniweverse.user.entity.ArtistProfile;
import com.miniweverse.user.entity.User;
import java.time.LocalDateTime;

/**
 * content/locked: 캐시(모든 사용자 공유)에는 항상 이 원본(잠금 없는) 형태로 저장하고, 서빙 직전에
 * 조회자의 구독 여부에 따라 {@link #locked()}로 마스킹한 사본을 내려준다 — 캐시 자체에 마스킹 결과를
 * 반영하면 먼저 캐시를 채운 사용자의 열람 권한이 이후 사용자에게도 그대로 적용되는 문제가 생긴다.
 */
public record PostResponse(
        Long postId,
        Long authorId,
        String authorNickname,
        Long artistId,
        BoardType boardType,
        String content,
        LocalDateTime createdAt,
        boolean membersOnly,
        boolean locked
) {
    private static final String WITHDRAWN_AUTHOR_NICKNAME = "탈퇴한 사용자";

    public static PostResponse from(Post post) {
        User author = post.getAuthor();
        ArtistProfile artistProfile = post.getArtistProfile();
        return new PostResponse(
                post.getId(),
                author != null ? author.getId() : null,
                author != null ? author.getNickname() : WITHDRAWN_AUTHOR_NICKNAME,
                artistProfile != null ? artistProfile.getId() : null,
                post.getBoardType(),
                post.getContent(),
                post.getCreatedAt(),
                post.isMembersOnly(),
                false
        );
    }

    public PostResponse mask() {
        return new PostResponse(
                postId, authorId, authorNickname, artistId, boardType, null, createdAt, membersOnly, true);
    }
}
