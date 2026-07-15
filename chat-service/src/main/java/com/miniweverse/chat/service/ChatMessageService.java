package com.miniweverse.chat.service;

import com.miniweverse.chat.dto.ChatMessageResponse;
import com.miniweverse.chat.entity.ChatMessage;
import com.miniweverse.chat.entity.ChatRoom;
import com.miniweverse.chat.membership.MembershipVerifier;
import com.miniweverse.chat.repository.ChatMessageRepository;
import com.miniweverse.chat.repository.ChatRoomRepository;
import com.miniweverse.exception.ChatExceptions.InvalidRequestException;
import com.miniweverse.exception.ChatExceptions.MembershipRequiredException;
import com.miniweverse.exception.ChatExceptions.RoomNotFoundException;
import com.miniweverse.user.enums.Role;
import java.util.List;
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

    @Transactional
    public ChatMessageResponse sendFanMessage(Long artistId, Long fanUserId, String content) {
        ChatRoom room = getRoom(artistId);
        if (!membershipVerifier.isActiveMember(fanUserId, artistId)) {
            throw new MembershipRequiredException();
        }
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
     * 팬이 조회하면 본인 스레드(fanUserIdFilter 무시, viewerId 사용), 아티스트(방 소유주)가 조회하면
     * 어느 팬의 스레드인지 fanUserIdFilter로 반드시 지정해야 한다 — 한 방 안에 팬별로 스레드가 갈리기 때문.
     */
    @Transactional(readOnly = true)
    public List<ChatMessageResponse> getMessages(Long artistId, Long viewerId, Long fanUserIdFilter) {
        ChatRoom room = getRoom(artistId);
        boolean viewerIsOwner = room.getOwnerUserId().equals(viewerId);

        Long targetFanId;
        if (viewerIsOwner) {
            if (fanUserIdFilter == null) {
                throw new InvalidRequestException("아티스트는 조회할 팬(fanId)을 지정해야 합니다.");
            }
            targetFanId = fanUserIdFilter;
        } else {
            if (!membershipVerifier.isActiveMember(viewerId, artistId)) {
                throw new MembershipRequiredException();
            }
            targetFanId = viewerId;
        }

        return chatMessageRepository.findVisibleMessages(room.getId(), targetFanId).stream()
                .map(ChatMessageResponse::from)
                .toList();
    }

    private ChatRoom getRoom(Long artistId) {
        return chatRoomRepository.findByArtistId(artistId)
                .orElseThrow(RoomNotFoundException::new);
    }
}
