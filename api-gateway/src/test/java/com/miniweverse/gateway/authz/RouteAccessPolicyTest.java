package com.miniweverse.gateway.authz;

import static org.assertj.core.api.Assertions.assertThat;

import com.miniweverse.common.security.jwt.Role;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

@Tag("unit")
class RouteAccessPolicyTest {

    private final RouteAccessPolicy policy = new RouteAccessPolicy();

    @Test
    void admin_경로는_SKIP이라_이_필터가_관여하지_않는다() {
        AccessRule rule = policy.resolve(HttpMethod.GET, "/admin/users");

        assertThat(rule.type()).isEqualTo(AccessType.SKIP);
    }

    @Test
    void login_경로는_인증_없이_공개된다() {
        AccessRule rule = policy.resolve(HttpMethod.POST, "/login");

        assertThat(rule.type()).isEqualTo(AccessType.PUBLIC);
    }

    @Test
    void GET_artists는_공개지만_같은_경로의_POST는_공개가_아니다() {
        AccessRule getRule = policy.resolve(HttpMethod.GET, "/artists");
        AccessRule postRule = policy.resolve(HttpMethod.POST, "/artists");

        assertThat(getRule.type()).isEqualTo(AccessType.PUBLIC);
        assertThat(postRule.type()).isEqualTo(AccessType.AUTHENTICATED);
    }

    @Test
    void GET_posts_단건조회는_공개된다() {
        AccessRule rule = policy.resolve(HttpMethod.GET, "/posts/42");

        assertThat(rule.type()).isEqualTo(AccessType.PUBLIC);
    }

    @Test
    void chat_api는_FAN과_ARTIST만_허용하는_ROLE_RESTRICTED다() {
        AccessRule rule = policy.resolve(HttpMethod.GET, "/api/chat/rooms");

        assertThat(rule.type()).isEqualTo(AccessType.ROLE_RESTRICTED);
        assertThat(rule.allowedRoles()).containsExactlyInAnyOrder(Role.FAN, Role.ARTIST);
    }

    @Test
    void 규칙에_없는_경로는_기본값인_AUTHENTICATED로_떨어진다() {
        AccessRule rule = policy.resolve(HttpMethod.GET, "/some/undefined/path");

        assertThat(rule.type()).isEqualTo(AccessType.AUTHENTICATED);
    }
}
