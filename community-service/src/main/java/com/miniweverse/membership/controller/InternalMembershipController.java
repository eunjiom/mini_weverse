package com.miniweverse.membership.controller;

import com.miniweverse.membership.dto.MembershipActiveResponse;
import com.miniweverse.membership.service.MembershipService;
import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * chat-service 같은 내부 서비스가 직접(게이트웨이를 거치지 않고) 호출하는 서비스 간 전용 API.
 * 외부에 노출되지 않는 내부망 신뢰를 전제로 하며, 그래서 유저 JWT 인증을 요구하지 않는다
 * (SecurityConfig의 permitAll 목록 참고). 공개용 스웨거 문서(브로셔 링크)에는 혼란을 줄 수 있어
 * {@link Hidden}으로 제외한다.
 */
@RestController
@Hidden
@Tag(name = "내부 API", description = "다른 서비스가 게이트웨이를 거치지 않고 직접 호출하는 서비스 간 전용 API. 유저 JWT 인증 없이 내부망 신뢰를 전제로 동작합니다.")
public class InternalMembershipController {

    private final MembershipService membershipService;

    public InternalMembershipController(MembershipService membershipService) {
        this.membershipService = membershipService;
    }

    @Operation(summary = "멤버십 활성 여부 확인 (내부 전용)", description = "chat-service 등 내부 서비스가 특정 유저의 특정 아티스트 멤버십 활성 여부를 확인할 때 사용합니다.")
    @GetMapping("/internal/memberships/active")
    public ResponseEntity<MembershipActiveResponse> isActive(
            @RequestParam Long subscriberId,
            @RequestParam Long artistId
    ) {
        boolean active = membershipService.isActive(subscriberId, artistId);
        return ResponseEntity.ok(new MembershipActiveResponse(active));
    }
}
