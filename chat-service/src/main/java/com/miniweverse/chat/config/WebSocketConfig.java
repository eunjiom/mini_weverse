package com.miniweverse.chat.config;

import com.miniweverse.auth.jwt.JwtTokenProvider;
import com.miniweverse.chat.websocket.ChatHandshakeHandler;
import com.miniweverse.chat.websocket.JwtHandshakeInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final JwtTokenProvider jwtTokenProvider;

    public WebSocketConfig(JwtTokenProvider jwtTokenProvider) {
        this.jwtTokenProvider = jwtTokenProvider;
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic");
        registry.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // TODO: 프론트 도메인이 정해지면 allowed origin을 실제 도메인으로 좁힌다 (Cross-Site WebSocket Hijacking 방어).
        registry.addEndpoint("/api/chat/ws-chat")
                .addInterceptors(new JwtHandshakeInterceptor(jwtTokenProvider))
                .setHandshakeHandler(new ChatHandshakeHandler())
                .setAllowedOriginPatterns("http://localhost:*");
    }
}
