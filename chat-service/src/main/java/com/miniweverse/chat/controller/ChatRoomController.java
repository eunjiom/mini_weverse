package com.miniweverse.chat.controller;

import com.miniweverse.chat.dto.ChatMessageResponse;
import com.miniweverse.chat.service.ChatMessageService;
import com.miniweverse.common.response.CursorPageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "채팅방")
@RestController
@Validated
@SecurityRequirement(name = "bearerAuth")
public class ChatRoomController {

    private final ChatMessageService chatMessageService;

    public ChatRoomController(ChatMessageService chatMessageService) {
        this.chatMessageService = chatMessageService;
    }

    @Operation(
            summary = "내 채팅 메시지 이력 조회",
            description = "팬 본인이 이 아티스트와 나눈 대화 이력을 커서 기반으로 조회한다. "
                    + "cursor 미지정 시 최신 메시지부터, 이후엔 그 id보다 작은(더 오래된) 메시지를 가져온다."
    )
    @GetMapping("/api/chat/rooms/{artistId}/messages")
    public ResponseEntity<CursorPageResponse<ChatMessageResponse>> getMyMessages(
            @AuthenticationPrincipal Long viewerId,
            @PathVariable Long artistId,
            @RequestParam(required = false) Long cursor,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size
    ) {
        return ResponseEntity.ok(chatMessageService.getMyMessages(artistId, viewerId, cursor, size));
    }

    @Operation(
            summary = "아티스트 인박스 전체 메시지 조회",
            description = "이 방을 소유한 아티스트가 팬 구분 없이 방에 오간 메시지 전체를 커서 기반으로 조회한다. "
                    + "cursor 미지정 시 최신 메시지부터, 이후엔 그 id보다 작은(더 오래된) 메시지를 가져온다."
    )
    @GetMapping("/api/chat/rooms/{artistId}/inbox")
    public ResponseEntity<CursorPageResponse<ChatMessageResponse>> getInboxMessages(
            @AuthenticationPrincipal Long viewerId,
            @PathVariable Long artistId,
            @RequestParam(required = false) Long cursor,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size
    ) {
        return ResponseEntity.ok(chatMessageService.getInboxMessages(artistId, viewerId, cursor, size));
    }
}
