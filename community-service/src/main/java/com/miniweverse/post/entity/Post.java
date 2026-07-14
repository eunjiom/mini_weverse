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
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.NotFound;
import org.hibernate.annotations.NotFoundAction;
import org.hibernate.annotations.SQLRestriction;

/**
 * boardType=ARTIST(아티스트 게시판)는 그 아티스트 본인만 쓸 수 있다는 규칙은 여기서 검증한다.
 * boardType=FEED(팬 게시판)는 팔로우 여부를 확인해야 하는데, 그건 Follow 조회가 필요해서
 * 엔티티가 아니라 PostService에서 검증한다.
 * deletedAt은 User/ArtistProfile과 동일한 soft delete 패턴 — Comment가 post_id FK를 가지고 있어
 * hard delete 시 댓글이 있으면 제약 위반이 나는 문제를 피한다.
 */
@Entity
@Table(name = "posts")
@SQLRestriction("deleted_at IS NULL")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Post extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * author가 탈퇴(soft delete)해도 게시글 자체는 남아있어야 하므로, User의
     * {@code @SQLRestriction}에 걸려 로딩이 안 되는 경우 예외 대신 null로 취급한다.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "author_id", nullable = false)
    @NotFound(action = NotFoundAction.IGNORE)
    private User author;

    /**
     * artistProfile(및 그 소유주 User)이 탈퇴/삭제돼도 게시글 자체는 남아있어야 하므로,
     * 예외 대신 null로 취급한다(ArtistProfile/User @SQLRestriction과의 상호작용).
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "artist_profile_id", nullable = false)
    @NotFound(action = NotFoundAction.IGNORE)
    private ArtistProfile artistProfile;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BoardType boardType;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    // 기존 게시글이 있는 테이블에 NOT NULL 컬럼을 기본값 없이 추가하면 ALTER TABLE 자체가 실패한다
    // (기존 row가 NULL이 되어 제약 위반) — DEFAULT false를 명시해 기존 row를 자동으로 채우게 한다.
    @Column(nullable = false, columnDefinition = "boolean not null default false")
    private boolean membersOnly;

    private LocalDateTime deletedAt;

    private Post(User author, ArtistProfile artistProfile, BoardType boardType, String content, boolean membersOnly) {
        this.author = author;
        this.artistProfile = artistProfile;
        this.boardType = boardType;
        this.content = content;
        this.membersOnly = membersOnly;
    }

    public static Post create(
            User author, ArtistProfile artistProfile, BoardType boardType, String content, boolean membersOnly
    ) {
        if (author == null || artistProfile == null || boardType == null) {
            throw new InvalidRequestException("author, artistProfile, boardType은 필수입니다.");
        }
        if (content == null || content.isBlank()) {
            throw new InvalidRequestException("본문은 필수입니다.");
        }
        if (boardType == BoardType.ARTIST && !author.getId().equals(artistProfile.getUser().getId())) {
            throw new InvalidRequestException("아티스트 게시판은 본인만 작성할 수 있습니다.");
        }
        if (membersOnly && boardType != BoardType.ARTIST) {
            throw new InvalidRequestException("멤버십 전용 글은 아티스트 게시판에만 작성할 수 있습니다.");
        }
        return new Post(author, artistProfile, boardType, content, membersOnly);
    }

    public void updateContent(String content) {
        if (content == null || content.isBlank()) {
            throw new InvalidRequestException("본문은 필수입니다.");
        }
        this.content = content;
    }

    public void delete() {
        this.deletedAt = LocalDateTime.now();
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }
}
