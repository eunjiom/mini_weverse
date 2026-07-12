package com.miniweverse.post.config;

import com.miniweverse.post.dto.PostResponse;
import java.time.Duration;
import java.util.List;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.JacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.type.TypeFactory;

@Configuration
@EnableCaching
public class CacheConfig {

    private static final String POSTS_CACHE = "posts";

    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        // 캐시 값(레코드 DTO)은 Serializable을 구현하지 않으므로 JDK 직렬화 대신 JSON을 쓴다.
        // 타입 태그 없이 캐시별로 정확한 타입을 지정해서 직렬화한다 (범용 폴리모픽 역직렬화는 쓰지 않음).
        JavaType postListType = TypeFactory.createDefaultInstance()
                .constructCollectionType(List.class, PostResponse.class);
        RedisCacheConfiguration postsCacheConfig = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(10))
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(
                        new JacksonJsonRedisSerializer<List<PostResponse>>(postListType)));

        return RedisCacheManager.builder(connectionFactory)
                .withCacheConfiguration(POSTS_CACHE, postsCacheConfig)
                .build();
    }
}
