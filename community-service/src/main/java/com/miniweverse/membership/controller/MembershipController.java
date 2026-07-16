package com.miniweverse.membership.controller;

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

    public MembershipController(MembershipService membershipService) {
        this.membershipService = membershipService;
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
        // chat-service 알림은 MembershipService.subscribe()가 같은 트랜잭션에서 아웃박스에 적재하고,
        // MembershipOutboxPublisher가 별도로 전송/재시도한다.
        Membership membership = membershipService.subscribe(subscriberId, request.artistId());
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
