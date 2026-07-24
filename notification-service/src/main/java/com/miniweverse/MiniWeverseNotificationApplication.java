package com.miniweverse;

import com.miniweverse.common.notification.outbox.NotificationOutboxEventProcessor;
import com.miniweverse.common.notification.outbox.NotificationOutboxPublisher;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.kafka.annotation.EnableKafka;

/**
 * NotificationOutboxEventProcessor/Publisher는 발행 측(community-service/chat-service)
 * 전용이라 NotificationOutboxEventRepository(JPA) 빈이 필요한데, 이 서비스는 DB가 없어
 * JPA 자동 설정 자체를 꺼뒀다(application.yml 참고) — 그대로 두면 그 두 빈이 의존성을 못 찾아
 * 기동이 실패한다(실행 중 실제로 확인). common 패키지 하위가 전부 자동 스캔되니 여기서만
 * 명시적으로 제외한다.
 */
@EnableKafka
@ConfigurationPropertiesScan
@SpringBootApplication
@ComponentScan(excludeFilters = @ComponentScan.Filter(
        type = FilterType.ASSIGNABLE_TYPE,
        classes = {NotificationOutboxEventProcessor.class, NotificationOutboxPublisher.class}
))
public class MiniWeverseNotificationApplication {

    public static void main(String[] args) {
        SpringApplication.run(MiniWeverseNotificationApplication.class, args);
    }

}
