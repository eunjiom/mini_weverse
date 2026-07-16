package com.miniweverse.chat.config;

import com.miniweverse.auth.jwt.JwtTokenProvider;
import com.miniweverse.chat.websocket.ChatChannelInterceptor;
import com.miniweverse.chat.websocket.ChatHandshakeHandler;
import com.miniweverse.chat.websocket.ChatOutboundChannelInterceptor;
import com.miniweverse.chat.websocket.JwtHandshakeInterceptor;
import org.springframework.beans.factory.annotation.Value;
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
    private final String[] allowedOriginPatterns;

    public WebSocketConfig(
            JwtTokenProvider jwtTokenProvider,
            ChatChannelInterceptor chatChannelInterceptor,
            ChatOutboundChannelInterceptor chatOutboundChannelInterceptor,
            @Value("${chat.websocket.allowed-origins:http://localhost:*}") String allowedOrigins
    ) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.chatChannelInterceptor = chatChannelInterceptor;
        this.chatOutboundChannelInterceptor = chatOutboundChannelInterceptor;
        this.allowedOriginPatterns = allowedOrigins.split(",");
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // /queue는 convertAndSendToUser(개인 큐 전달)가 내부적으로 쓰는 prefix라 같이 등록해야 한다.
        registry.enableSimpleBroker("/topic", "/queue");
        registry.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // 프론트 도메인이 정해지면 chat.websocket.allowed-origins 설정값만 바꾸면 된다 (Cross-Site WebSocket Hijacking 방어).
        registry.addEndpoint("/api/chat/ws-chat")
                .addInterceptors(new JwtHandshakeInterceptor(jwtTokenProvider))
                .setHandshakeHandler(new ChatHandshakeHandler())
                .setAllowedOriginPatterns(allowedOriginPatterns);
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
