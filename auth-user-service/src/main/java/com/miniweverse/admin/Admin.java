package com.miniweverse.admin;

import com.miniweverse.common.BaseTimeEntity;
import com.miniweverse.exception.InvalidRequestException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 일반 유저(User)와 완전히 분리된 관리자 계정. 세션 기반으로 인증한다.
 */
@Entity
@Table(name = "admins")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Admin extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String password;

    private Admin(String email, String password) {
        this.email = email;
        this.password = password;
    }

    public static Admin create(String email, String password) {
        if (email == null || email.isBlank()) {
            throw new InvalidRequestException("이메일은 필수입니다.");
        }
        if (password == null || password.isBlank()) {
            throw new InvalidRequestException("비밀번호는 필수입니다.");
        }
        return new Admin(email, password);
    }
}
