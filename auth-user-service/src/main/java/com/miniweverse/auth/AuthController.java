package com.miniweverse.auth;

import com.miniweverse.auth.dto.LoginRequest;
import com.miniweverse.auth.dto.SignupRequest;
import com.miniweverse.auth.dto.SignupResponse;
import com.miniweverse.auth.dto.TokenResponse;
import com.miniweverse.user.entity.User;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/signup")
    public ResponseEntity<SignupResponse> signup(@Valid @RequestBody SignupRequest request) {
        User user = authService.signup(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new SignupResponse(user.getId(), user.getNickname()));
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        LoginResult result = authService.login(request.email(), request.password());

        if (result instanceof LoginResult.UserLoginResult userLogin) {
            return ResponseEntity.ok()
                    .header(HttpHeaders.SET_COOKIE, userLogin.refreshTokenCookie().toString())
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + userLogin.accessToken())
                    .body(new TokenResponse(userLogin.accessToken()));
        }

        establishAdminSession((LoginResult.AdminLoginResult) result, httpRequest);
        return ResponseEntity.ok().build();
    }

    private void establishAdminSession(LoginResult.AdminLoginResult adminLogin, HttpServletRequest request) {
        Authentication authentication = new UsernamePasswordAuthenticationToken(
                adminLogin.admin().getEmail(), null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);

        HttpSession session = request.getSession(true);
        session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context);
    }
}
