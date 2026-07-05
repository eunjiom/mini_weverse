package com.miniweverse.user.entity;

import com.miniweverse.common.BaseTimeEntity;
import com.miniweverse.exception.AuthUserExceptions.NotArtistException;
import com.miniweverse.exception.AuthUserExceptions.SelfFollowNotAllowedException;
import com.miniweverse.user.enums.Role;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(
        name = "follows",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_follows_follower_artist",
                columnNames = {"follower_id", "artist_id"}
        )
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Follow extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "follower_id", nullable = false)
    private User follower;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "artist_id", nullable = false)
    private User artist;

    private Follow(User follower, User artist) {
        this.follower = follower;
        this.artist = artist;
    }

    public static Follow create(User follower, User artist) {
        boolean isSelfFollow = follower == artist
                || (follower.getId() != null && Objects.equals(follower.getId(), artist.getId()));
        if (isSelfFollow) {
            throw new SelfFollowNotAllowedException();
        }
        if (artist.getRole() != Role.ARTIST) {
            throw new NotArtistException();
        }
        return new Follow(follower, artist);
    }
}
