package com.miniweverse.user.entity;

import com.miniweverse.common.entity.BaseTimeEntity;
import com.miniweverse.exception.AuthUserExceptions.InvalidRequestException;
import com.miniweverse.user.enums.AuthProvider;
import com.miniweverse.common.security.jwt.Role;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.SQLRestriction;

/**
 * deletedAt IS NULL 조건이 이 엔티티의 모든 조회(연관관계 조회 포함)에 전역으로 적용된다({@link SQLRestriction}).
 * 탈퇴한 유저를 포함해 조회해야 한다면 별도의 네이티브 쿼리가 필요하다.
 */
@Entity
@Table(name = "users")
@SQLRestriction("deleted_at IS NULL")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String email;

    private String password;

    @Column(nullable = false)
    private String nickname;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AuthProvider provider;

    // 유니크 제약은 schema.sql의 uk_users_provider_id(부분 인덱스)로 건다 — email과 동일한 방식.
    private String providerId;

    private LocalDateTime deletedAt;

    private User(String email, String password, String nickname, Role role, AuthProvider provider, String providerId) {
        this.email = email;
        this.password = password;
        this.nickname = nickname;
        this.role = role;
        this.provider = provider;
        this.providerId = providerId;
    }

    public static User createLocal(String email, String password, String nickname, Role role) {
        validateEmailAndNickname(email, nickname);
        if (password == null || password.isBlank()) {
            throw new InvalidRequestException("로컬 가입은 비밀번호가 필요합니다.");
        }
        return new User(email, password, nickname, role, AuthProvider.LOCAL, null);
    }

    public static User createKakao(String email, String providerId, String nickname, Role role) {
        validateEmailAndNickname(email, nickname);
        if (providerId == null || providerId.isBlank()) {
            throw new InvalidRequestException("카카오 가입은 providerId가 필요합니다.");
        }
        return new User(email, null, nickname, role, AuthProvider.KAKAO, providerId);
    }

    private static void validateEmailAndNickname(String email, String nickname) {
        if (email == null || email.isBlank()) {
            throw new InvalidRequestException("이메일은 필수입니다.");
        }
        if (nickname == null || nickname.isBlank()) {
            throw new InvalidRequestException("닉네임은 필수입니다.");
        }
    }

    public void delete() {
        this.deletedAt = LocalDateTime.now();
        this.email = "deleted_" + id + "@withdrawn.local";
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }
}
