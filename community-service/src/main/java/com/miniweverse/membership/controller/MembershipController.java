package com.miniweverse.membership.controller;

import com.miniweverse.membership.client.ChatServiceMembershipNotifier;
import com.miniweverse.membership.dto.MembershipResponse;
import com.miniweverse.membership.dto.MyMembershipResponse;
import com.miniweverse.membership.dto.SubscribeRequest;
import com.miniweverse.membership.entity.Membership;
import com.miniweverse.membership.service.MembershipService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class MembershipController {

    private final MembershipService membershipService;
    private final ChatServiceMembershipNotifier chatServiceMembershipNotifier;

    public MembershipController(MembershipService membershipService, ChatServiceMembershipNotifier chatServiceMembershipNotifier) {
        this.membershipService = membershipService;
        this.chatServiceMembershipNotifier = chatServiceMembershipNotifier;
    }

    @GetMapping("/memberships")
    public ResponseEntity<List<MyMembershipResponse>> getMyMemberships(
            @AuthenticationPrincipal Long subscriberId
    ) {
        return ResponseEntity.ok(membershipService.getMyMemberships(subscriberId));
    }

    @PostMapping("/memberships")
    public ResponseEntity<MembershipResponse> subscribe(
            @AuthenticationPrincipal Long subscriberId,
            @Valid @RequestBody SubscribeRequest request
    ) {
        Membership membership = membershipService.subscribe(subscriberId, request.artistId());
        // 서비스 메서드가 커밋까지 끝난 뒤(트랜잭션 프록시 반환 후) 호출 — 롤백된 구독을 활성으로 잘못 캐싱하지 않도록.
        chatServiceMembershipNotifier.notifyActivated(subscriberId, request.artistId());
        return ResponseEntity.ok(MembershipResponse.from(membership));
    }

    @PostMapping("/memberships/{membershipId}/cancel")
    public ResponseEntity<Void> cancel(
            @AuthenticationPrincipal Long subscriberId,
            @PathVariable Long membershipId
    ) {
        membershipService.cancel(membershipId, subscriberId);
        return ResponseEntity.ok().build();
    }
}
