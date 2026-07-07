package com.miniweverse.post.entity;

import com.miniweverse.common.BaseTimeEntity;
import com.miniweverse.exception.AuthUserExceptions.InvalidRequestException;
import com.miniweverse.post.enums.BoardType;
import com.miniweverse.user.entity.ArtistProfile;
import com.miniweverse.user.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * boardType=ARTIST(아티스트 게시판)는 그 아티스트 본인만 쓸 수 있다는 규칙은 여기서 검증한다.
 * boardType=FEED(팬 게시판)는 팔로우 여부를 확인해야 하는데, 그건 Follow 조회가 필요해서
 * 엔티티가 아니라 PostService에서 검증한다.
 */
@Entity
@Table(name = "posts")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Post extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "author_id", nullable = false)
    private User author;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "artist_profile_id", nullable = false)
    private ArtistProfile artistProfile;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BoardType boardType;

    @Column(nullable = false)
    private String content;

    private Post(User author, ArtistProfile artistProfile, BoardType boardType, String content) {
        this.author = author;
        this.artistProfile = artistProfile;
        this.boardType = boardType;
        this.content = content;
    }

    public static Post create(User author, ArtistProfile artistProfile, BoardType boardType, String content) {
        if (author == null || artistProfile == null || boardType == null) {
            throw new InvalidRequestException("author, artistProfile, boardType은 필수입니다.");
        }
        if (content == null || content.isBlank()) {
            throw new InvalidRequestException("본문은 필수입니다.");
        }
        if (boardType == BoardType.ARTIST && !author.getId().equals(artistProfile.getUser().getId())) {
            throw new InvalidRequestException("아티스트 게시판은 본인만 작성할 수 있습니다.");
        }
        return new Post(author, artistProfile, boardType, content);
    }
}
