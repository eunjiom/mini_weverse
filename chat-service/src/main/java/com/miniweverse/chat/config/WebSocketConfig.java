package com.miniweverse.chat.config;

import com.miniweverse.auth.jwt.JwtTokenProvider;
import com.miniweverse.chat.websocket.ChatChannelInterceptor;
import com.miniweverse.chat.websocket.ChatHandshakeHandler;
import com.miniweverse.chat.websocket.ChatOutboundChannelInterceptor;
import com.miniweverse.chat.websocket.JwtHandshakeInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final JwtTokenProvider jwtTokenProvider;
    private final ChatChannelInterceptor chatChannelInterceptor;
    private final ChatOutboundChannelInterceptor chatOutboundChannelInterceptor;

    public WebSocketConfig(
            JwtTokenProvider jwtTokenProvider,
            ChatChannelInterceptor chatChannelInterceptor,
            ChatOutboundChannelInterceptor chatOutboundChannelInterceptor
    ) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.chatChannelInterceptor = chatChannelInterceptor;
        this.chatOutboundChannelInterceptor = chatOutboundChannelInterceptor;
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // /queue는 convertAndSendToUser(개인 큐 전달)가 내부적으로 쓰는 prefix라 같이 등록해야 한다.
        registry.enableSimpleBroker("/topic", "/queue");
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

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(chatChannelInterceptor);
    }

    @Override
    public void configureClientOutboundChannel(ChannelRegistration registration) {
        registration.interceptors(chatOutboundChannelInterceptor);
    }
}
