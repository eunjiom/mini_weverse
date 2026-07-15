package com.miniweverse.chat.controller;

import com.miniweverse.chat.dto.ChatMessageResponse;
import com.miniweverse.chat.service.ChatMessageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "채팅방")
@RestController
public class ChatRoomController {

    private final ChatMessageService chatMessageService;

    public ChatRoomController(ChatMessageService chatMessageService) {
        this.chatMessageService = chatMessageService;
    }

    @Operation(summary = "채팅 메시지 이력 조회", description = "팬은 본인 스레드만, 아티스트는 fanId로 지정한 팬의 스레드를 조회한다.")
    @GetMapping("/api/chat/rooms/{artistId}/messages")
    public ResponseEntity<List<ChatMessageResponse>> getMessages(
            @AuthenticationPrincipal Long viewerId,
            @PathVariable Long artistId,
            @Parameter(description = "아티스트가 조회할 때만 필요 — 어느 팬의 스레드인지")
            @RequestParam(required = false) Long fanId
    ) {
        return ResponseEntity.ok(chatMessageService.getMessages(artistId, viewerId, fanId));
    }
}
