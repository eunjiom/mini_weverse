package com.miniweverse.chat.service;

import com.miniweverse.chat.dto.ChatMessageResponse;
import com.miniweverse.chat.entity.ChatMessage;
import com.miniweverse.chat.entity.ChatRoom;
import com.miniweverse.chat.membership.MembershipPeriodRepository;
import com.miniweverse.chat.membership.MembershipVerifier;
import com.miniweverse.chat.repository.ChatMessageRepository;
import com.miniweverse.chat.repository.ChatRoomRepository;
import com.miniweverse.common.notification.NotificationType;
import com.miniweverse.common.notification.outbox.NotificationOutboxEvent;
import com.miniweverse.common.notification.outbox.NotificationOutboxEventRepository;
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

    private static final String NEW_CHAT_MESSAGE_TITLE = "새 메시지";

    private final ChatRoomRepository chatRoomRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final MembershipVerifier membershipVerifier;
    private final MembershipPeriodRepository membershipPeriodRepository;
    private final NotificationOutboxEventRepository notificationOutboxEventRepository;

    public ChatMessageService(
            ChatRoomRepository chatRoomRepository,
            ChatMessageRepository chatMessageRepository,
            MembershipVerifier membershipVerifier,
            MembershipPeriodRepository membershipPeriodRepository,
            NotificationOutboxEventRepository notificationOutboxEventRepository
    ) {
        this.chatRoomRepository = chatRoomRepository;
        this.chatMessageRepository = chatMessageRepository;
        this.membershipVerifier = membershipVerifier;
        this.membershipPeriodRepository = membershipPeriodRepository;
        this.notificationOutboxEventRepository = notificationOutboxEventRepository;
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
        // 팬 메시지의 수신자는 방 주인(아티스트) 1명뿐인데, 아티스트 대상 알림은 지원하지 않는다.
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
        // 아티스트의 방송은 지금 활성 구독 중인 팬 전원에게 fan-out한다.
        membershipPeriodRepository.findFanUserIdsWithOpenPeriod(artistId).forEach(fanUserId ->
                notificationOutboxEventRepository.save(NotificationOutboxEvent.of(
                        NotificationType.NEW_CHAT_MESSAGE, fanUserId, NEW_CHAT_MESSAGE_TITLE, "구독 중인 아티스트가 메시지를 보냈습니다."
                ))
        );
        return ChatMessageResponse.from(message);
    }

    /**
     * 팬 본인의 스레드 조회 전용 — 특정 팬 하나를 콕 집어 아티스트가 그 대화만 따로 조회하는 기능은
     * 요청받은 적 없어서 만들지 않는다(팬 입장에서 "내 채팅 화면 열기"만 지원). 아티스트가 방 전체를
     * 보는 건 {@link #getInboxMessages}가 별도로 담당한다.
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

    /**
     * 아티스트 본인의 방 전체 조회 — 팬별 필터 없이 방에 오간 메시지 전부를 본다. 아티스트는 자기
     * 방 주인이라 팬처럼 "구독 기간 안이었는지" 따질 필요가 없다(자기가 보낸 방송이든, 어느 팬이
     * 보낸 답장이든 전부 자기 방 소유 권한만으로 볼 자격이 있음).
     */
    public CursorPageResponse<ChatMessageResponse> getInboxMessages(Long artistId, Long artistUserId, Long cursor, int size) {
        ChatRoom room = getRoom(artistId);
        if (!room.getOwnerUserId().equals(artistUserId)) {
            throw new InvalidRequestException("본인 소유의 채팅방이 아닙니다.");
        }
        List<ChatMessageResponse> fetched = chatMessageRepository
                .findAllByRoomId(room.getId(), cursor, PageRequest.of(0, size + 1))
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
