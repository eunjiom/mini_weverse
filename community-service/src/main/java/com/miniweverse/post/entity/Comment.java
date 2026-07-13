package com.miniweverse.post.entity;

import com.miniweverse.common.BaseTimeEntity;
import com.miniweverse.exception.AuthUserExceptions.InvalidRequestException;
import com.miniweverse.user.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
import org.hibernate.annotations.NotFound;
import org.hibernate.annotations.NotFoundAction;

/**
 * 댓글은 게시판 종류(FEED/ARTIST) 상관없이 그 아티스트를 팔로우한 사람이면 누구나 달 수 있다.
 * 팔로우 여부는 Follow 조회가 필요해서 엔티티가 아니라 CommentService에서 검증한다.
 */
@Entity
@Table(name = "comments")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Comment extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "post_id", nullable = false)
    private Post post;

    /**
     * author가 탈퇴(soft delete)해도 댓글 자체는 남아있어야 하므로, User의
     * {@code @SQLRestriction}에 걸려 로딩이 안 되는 경우 예외 대신 null로 취급한다.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "author_id", nullable = false)
    @NotFound(action = NotFoundAction.IGNORE)
    private User author;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    private Comment(Post post, User author, String content) {
        this.post = post;
        this.author = author;
        this.content = content;
    }

    public static Comment create(Post post, User author, String content) {
        if (post == null || author == null) {
            throw new InvalidRequestException("post와 author는 필수입니다.");
        }
        if (content == null || content.isBlank()) {
            throw new InvalidRequestException("본문은 필수입니다.");
        }
        return new Comment(post, author, content);
    }

    public void updateContent(String content) {
        if (content == null || content.isBlank()) {
            throw new InvalidRequestException("본문은 필수입니다.");
        }
        this.content = content;
    }
}
