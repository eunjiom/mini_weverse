package com.miniweverse.chat.membership;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * community-service가 구독 성공 직후 내부망에서 직접 호출(게이트웨이 미경유)하는 캐시 갱신 전용
 * API. community-service의 InternalMembershipController와 동일하게 내부망 신뢰를 전제로
 * 유저 JWT 인증 없이 열어둔다(SecurityConfig의 permitAll 목록 참고).
 */
@RestController
public class InternalMembershipPushController {

    private final MembershipVerifier membershipVerifier;

    public InternalMembershipPushController(MembershipVerifier membershipVerifier) {
        this.membershipVerifier = membershipVerifier;
    }

    @PostMapping("/internal/memberships/active")
    public ResponseEntity<Void> markActive(@RequestBody MembershipActivatedRequest request) {
        membershipVerifier.markActive(request.fanUserId(), request.artistId());
        return ResponseEntity.ok().build();
    }
}
