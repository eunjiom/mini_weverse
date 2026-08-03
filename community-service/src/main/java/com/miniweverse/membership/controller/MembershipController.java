package com.miniweverse.membership.controller;

import com.miniweverse.membership.dto.MembershipResponse;
import com.miniweverse.membership.dto.MyMembershipResponse;
import com.miniweverse.membership.dto.SubscribeRequest;
import com.miniweverse.membership.entity.Membership;
import com.miniweverse.membership.service.MembershipService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "멤버십", description = "아티스트 유료 멤버십 구독, 조회, 해지")
@SecurityRequirement(name = "bearerAuth")
public class MembershipController {

    private final MembershipService membershipService;

    public MembershipController(MembershipService membershipService) {
        this.membershipService = membershipService;
    }

    @Operation(summary = "내 멤버십 목록 조회", description = "내가 구독 중인 아티스트 멤버십 목록을 조회합니다.")
    @GetMapping("/memberships")
    public ResponseEntity<List<MyMembershipResponse>> getMyMemberships(
            @AuthenticationPrincipal Long subscriberId
    ) {
        return ResponseEntity.ok(membershipService.getMyMemberships(subscriberId));
    }

    @Operation(summary = "멤버십 구독", description = "특정 아티스트의 유료 멤버십을 구독합니다.")
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

    @Operation(summary = "멤버십 해지", description = "구독 중인 멤버십을 해지합니다. 해지 후에도 남은 구독 기간까지는 이용 가능합니다.")
    @PostMapping("/memberships/{membershipId}/cancel")
    public ResponseEntity<Void> cancel(
            @AuthenticationPrincipal Long subscriberId,
            @PathVariable Long membershipId
    ) {
        membershipService.cancel(membershipId, subscriberId);
        return ResponseEntity.ok().build();
    }
}
