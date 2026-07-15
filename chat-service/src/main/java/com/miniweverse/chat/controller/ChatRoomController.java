package com.miniweverse.chat.controller;

import com.miniweverse.chat.dto.ChatMessageResponse;
import com.miniweverse.chat.service.ChatMessageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "채팅방")
@RestController
public class ChatRoomController {

    private final ChatMessageService chatMessageService;

    public ChatRoomController(ChatMessageService chatMessageService) {
        this.chatMessageService = chatMessageService;
    }

    @Operation(summary = "내 채팅 메시지 이력 조회", description = "팬 본인이 이 아티스트와 나눈 대화 이력을 조회한다.")
    @GetMapping("/api/chat/rooms/{artistId}/messages")
    public ResponseEntity<List<ChatMessageResponse>> getMyMessages(
            @AuthenticationPrincipal Long viewerId,
            @PathVariable Long artistId
    ) {
        return ResponseEntity.ok(chatMessageService.getMyMessages(artistId, viewerId));
    }
}
