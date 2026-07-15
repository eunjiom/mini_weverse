package com.miniweverse.chat.entity;

import com.miniweverse.common.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 아티스트 한 명당 채팅방 하나. 아티스트 등록 자체가 API 없이 DB로 직접 이뤄지는 것과 같은 방식으로,
 * 이 row도 아티스트를 만들 때 같이 수동으로 생성한다(애플리케이션 코드로 생성하는 경로가 없다).
 */
@Entity
@Table(
        name = "chat_rooms",
        uniqueConstraints = @UniqueConstraint(name = "uk_chat_rooms_artist_id", columnNames = "artist_id")
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChatRoom extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** community-service ArtistProfile의 PK. 서비스가 분리되어 있어 FK 제약은 걸지 않는다. */
    @Column(name = "artist_id", nullable = false)
    private Long artistId;

    /** 이 방을 소유한 아티스트의 User.id(JWT의 sub와 동일) — artist-message SEND 인가에 쓴다. */
    @Column(name = "owner_user_id", nullable = false)
    private Long ownerUserId;
}
