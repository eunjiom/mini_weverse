package com.miniweverse.chat.controller;

import com.miniweverse.chat.broadcast.ChatBroadcastPublisher;
import com.miniweverse.chat.dto.ChatMessageResponse;
import com.miniweverse.chat.dto.ChatMessageSendRequest;
import com.miniweverse.chat.repository.ChatRoomRepository;
import com.miniweverse.chat.service.ChatMessageService;
import com.miniweverse.chat.websocket.ChatPrincipal;
import com.miniweverse.exception.ChatExceptions.RoomNotFoundException;
import jakarta.validation.Valid;
import java.security.Principal;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;

/**
 * 인가(방 접근 가능 여부)는 ChatChannelInterceptor가 SEND 시점에 이미 확인했으므로,
 * 여기서는 순수하게 메시지 저장 + 라우팅만 담당한다.
 *
 * 팬 메시지는 보낸 본인 + 아티스트에게만(개인 큐), 아티스트 메시지는 이 방을 구독 중인
 * 모두에게(topic 방송) 전달 — 위버스 DM 가시성 규칙 그대로.
 */
@Controller
public class ChatMessageStompController {

    private final ChatMessageService chatMessageService;
    private final ChatRoomRepository chatRoomRepository;
    private final ChatBroadcastPublisher broadcastPublisher;

    public ChatMessageStompController(
            ChatMessageService chatMessageService,
            ChatRoomRepository chatRoomRepository,
            ChatBroadcastPublisher broadcastPublisher
    ) {
        this.chatMessageService = chatMessageService;
        this.chatRoomRepository = chatRoomRepository;
        this.broadcastPublisher = broadcastPublisher;
    }

    @MessageMapping("/rooms/{artistId}/fan-message")
    public void sendFanMessage(
            @DestinationVariable Long artistId,
            @Valid @Payload ChatMessageSendRequest request,
            Principal principal
    ) {
        ChatPrincipal fan = (ChatPrincipal) principal;
        ChatMessageResponse response = chatMessageService.sendFanMessage(artistId, fan.userId(), request.content());

        Long ownerUserId = chatRoomRepository.findByArtistId(artistId)
                .orElseThrow(RoomNotFoundException::new)
                .getOwnerUserId();

        // 보낸 팬 본인에게 echo (본인 UI에 저장 확정 반영)
        broadcastPublisher.toUser(fan.getName(), "/queue/rooms/" + artistId, response);
        // 아티스트 inbox — senderId로 어느 팬 스레드인지 클라이언트가 구분
        broadcastPublisher.toUser(String.valueOf(ownerUserId), "/queue/rooms/" + artistId + "/inbox", response);
    }

    @MessageMapping("/rooms/{artistId}/artist-message")
    public void sendArtistMessage(
            @DestinationVariable Long artistId,
            @Valid @Payload ChatMessageSendRequest request,
            Principal principal
    ) {
        ChatPrincipal artist = (ChatPrincipal) principal;
        ChatMessageResponse response = chatMessageService.sendArtistMessage(artistId, artist.userId(), request.content());

        // 방송: 이 방(topic)을 구독 중인 모든 팬에게 동시 전달
        broadcastPublisher.toTopic("/topic/rooms/" + artistId + "/broadcast", response);
    }
}
