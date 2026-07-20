package com.miniweverse.chat.service;

import com.miniweverse.chat.dto.ChatMessageResponse;
import com.miniweverse.chat.entity.ChatMessage;
import com.miniweverse.chat.entity.ChatRoom;
import com.miniweverse.chat.membership.MembershipVerifier;
import com.miniweverse.chat.repository.ChatMessageRepository;
import com.miniweverse.chat.repository.ChatRoomRepository;
import com.miniweverse.common.response.CursorPageResponse;
import com.miniweverse.exception.ChatExceptions.InvalidRequestException;
import com.miniweverse.exception.ChatExceptions.MembershipRequiredException;
import com.miniweverse.exception.ChatExceptions.RoomNotFoundException;
import com.miniweverse.common.security.jwt.Role;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ChatMessageService {

    private final ChatRoomRepository chatRoomRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final MembershipVerifier membershipVerifier;

    public ChatMessageService(
            ChatRoomRepository chatRoomRepository,
            ChatMessageRepository chatMessageRepository,
            MembershipVerifier membershipVerifier
    ) {
        this.chatRoomRepository = chatRoomRepository;
        this.chatMessageRepository = chatMessageRepository;
        this.membershipVerifier = membershipVerifier;
    }

    // 멤버십 확인(캐시 미스 시 community-service 원격 호출)을 트랜잭션 밖에서 먼저 끝내서,
    // 네트워크 왕복 동안 DB 커넥션을 점유하지 않게 한다. 아래 리포지토리 호출들은 각자
    // Spring Data JPA가 기본으로 걸어주는 짧은 트랜잭션으로 처리된다.
    public ChatMessageResponse sendFanMessage(Long artistId, Long fanUserId, String content) {
        if (!membershipVerifier.isActiveMember(fanUserId, artistId)) {
            throw new MembershipRequiredException();
        }
        ChatRoom room = getRoom(artistId);
        ChatMessage message = ChatMessage.create(room, fanUserId, Role.FAN, content);
        chatMessageRepository.save(message);
        return ChatMessageResponse.from(message);
    }

    @Transactional
    public ChatMessageResponse sendArtistMessage(Long artistId, Long artistUserId, String content) {
        ChatRoom room = getRoom(artistId);
        if (!room.getOwnerUserId().equals(artistUserId)) {
            throw new InvalidRequestException("본인 소유의 채팅방이 아닙니다.");
        }
        ChatMessage message = ChatMessage.create(room, artistUserId, Role.ARTIST, content);
        chatMessageRepository.save(message);
        return ChatMessageResponse.from(message);
    }

    /**
     * 팬 본인의 스레드 조회 전용 — 아티스트가 특정 팬의 대화를 조회/검색하는 기능은 요청받은 적
     * 없어서 만들지 않는다(팬 입장에서 "내 채팅 화면 열기"만 지원).
     */
    public CursorPageResponse<ChatMessageResponse> getMyMessages(Long artistId, Long fanUserId, Long cursor, int size) {
        if (!membershipVerifier.isActiveMember(fanUserId, artistId)) {
            throw new MembershipRequiredException();
        }
        ChatRoom room = getRoom(artistId);
        List<ChatMessageResponse> fetched = chatMessageRepository
                .findVisibleMessages(room.getId(), artistId, fanUserId, cursor, PageRequest.of(0, size + 1))
                .stream()
                .map(ChatMessageResponse::from)
                .toList();
        return CursorPageResponse.of(fetched, size, ChatMessageResponse::id);
    }

    private ChatRoom getRoom(Long artistId) {
        return chatRoomRepository.findByArtistId(artistId)
                .orElseThrow(RoomNotFoundException::new);
    }
}
