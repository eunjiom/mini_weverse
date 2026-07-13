package com.miniweverse.user.entity;

import com.miniweverse.common.BaseTimeEntity;
import com.miniweverse.exception.AuthUserExceptions.InvalidArtistProfileException;
import com.miniweverse.user.enums.ArtistCategory;
import com.miniweverse.user.enums.Role;
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
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.NotFound;
import org.hibernate.annotations.NotFoundAction;
import org.hibernate.annotations.SQLRestriction;

/**
 * deletedAt은 User의 탈퇴와 별개다 — 아티스트 활동 중단/채널 폐쇄처럼 User 계정은 유지된 채
 * 아티스트 프로필만 삭제되는 경우가 있을 수 있어서 독립적인 soft delete 상태를 둔다.
 */
@Entity
@Table(name = "artist_profiles")
@SQLRestriction("deleted_at IS NULL")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ArtistProfile extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 유니크 제약은 schema.sql의 uk_artist_profiles_user_id(부분 인덱스)로 건다 — soft delete된 프로필은 제외해야 재생성 가능.
    // user가 탈퇴(soft delete)해도 프로필 참조는 예외 대신 null로 취급한다(User @SQLRestriction과의 상호작용).
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    @NotFound(action = NotFoundAction.IGNORE)
    private User user;

    @Column(nullable = false)
    private String channelName;

    private String introduction;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ArtistCategory category;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_id")
    private ArtistProfile group;

    private String profileImageUrl;

    private LocalDateTime deletedAt;

    private ArtistProfile(
            User user,
            String channelName,
            String introduction,
            ArtistCategory category,
            ArtistProfile group,
            String profileImageUrl
    ) {
        this.user = user;
        this.channelName = channelName;
        this.introduction = introduction;
        this.category = category;
        this.group = group;
        this.profileImageUrl = profileImageUrl;
    }

    public static ArtistProfile create(
            User user,
            String channelName,
            String introduction,
            ArtistCategory category,
            ArtistProfile group,
            String profileImageUrl
    ) {
        if (user == null) {
            throw new InvalidArtistProfileException("유저 정보가 필요합니다.");
        }
        if (category == null) {
            throw new InvalidArtistProfileException("카테고리는 필수입니다.");
        }
        if (user.getRole() != Role.ARTIST) {
            throw new InvalidArtistProfileException("ARTIST 권한을 가진 유저만 아티스트 프로필을 생성할 수 있습니다.");
        }
        validateGroup(category, group);
        return new ArtistProfile(user, channelName, introduction, category, group, profileImageUrl);
    }

    private static void validateGroup(ArtistCategory category, ArtistProfile group) {
        if (category == ArtistCategory.MEMBER) {
            if (group == null) {
                throw new InvalidArtistProfileException("MEMBER는 소속 그룹이 필요합니다.");
            }
            if (group.getCategory() != ArtistCategory.GROUP) {
                throw new InvalidArtistProfileException("소속 그룹은 GROUP 카테고리여야 합니다.");
            }
            return;
        }
        if (group != null) {
            throw new InvalidArtistProfileException("SOLO/GROUP은 소속 그룹을 가질 수 없습니다.");
        }
    }

    public void delete() {
        this.deletedAt = LocalDateTime.now();
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }
}
