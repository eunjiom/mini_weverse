package com.miniweverse.membership.controller;

import com.miniweverse.membership.dto.MembershipActiveResponse;
import com.miniweverse.membership.service.MembershipService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * chat-service 같은 내부 서비스가 직접(게이트웨이를 거치지 않고) 호출하는 서비스 간 전용 API.
 * 외부에 노출되지 않는 내부망 신뢰를 전제로 하며, 그래서 유저 JWT 인증을 요구하지 않는다
 * (SecurityConfig의 permitAll 목록 참고).
 */
@RestController
public class InternalMembershipController {

    private final MembershipService membershipService;

    public InternalMembershipController(MembershipService membershipService) {
        this.membershipService = membershipService;
    }

    @GetMapping("/internal/memberships/active")
    public ResponseEntity<MembershipActiveResponse> isActive(
            @RequestParam Long subscriberId,
            @RequestParam Long artistId
    ) {
        boolean active = membershipService.isActive(subscriberId, artistId);
        return ResponseEntity.ok(new MembershipActiveResponse(active));
    }
}
