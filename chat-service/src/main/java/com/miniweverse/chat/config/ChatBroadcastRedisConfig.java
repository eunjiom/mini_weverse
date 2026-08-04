package com.miniweverse.chat.config;

import com.miniweverse.chat.broadcast.ChatBroadcastSubscriber;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

@Configuration
public class ChatBroadcastRedisConfig {

    private static final String CHANNEL = "chat:broadcast";

    @Bean
    public ChannelTopic chatBroadcastTopic() {
        return new ChannelTopic(CHANNEL);
    }

    @Bean
    public RedisMessageListenerContainer chatBroadcastListenerContainer(
            RedisConnectionFactory connectionFactory,
            ChatBroadcastSubscriber subscriber,
            ChannelTopic chatBroadcastTopic
    ) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener(subscriber, chatBroadcastTopic);
        return container;
    }
}
