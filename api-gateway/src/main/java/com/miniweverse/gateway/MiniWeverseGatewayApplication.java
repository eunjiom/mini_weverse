package com.miniweverse.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.boot.data.jpa.autoconfigure.DataJpaRepositoriesAutoConfiguration;
import org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceInitializationAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceTransactionManagerAutoConfiguration;

/**
 * common 모듈이 BaseTimeEntity 때문에 JPA를 api 의존성으로 노출하고 있어서,
 * DB가 없는 게이트웨이에도 JPA 자동설정이 전이된다. 게이트웨이는 DataSource가
 * 없으므로 관련 자동설정을 명시적으로 제외한다.
 */
@SpringBootApplication(exclude = {
        HibernateJpaAutoConfiguration.class,
        DataSourceAutoConfiguration.class,
        DataSourceInitializationAutoConfiguration.class,
        DataSourceTransactionManagerAutoConfiguration.class,
        DataJpaRepositoriesAutoConfiguration.class
})
@ConfigurationPropertiesScan
public class MiniWeverseGatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(MiniWeverseGatewayApplication.class, args);
    }
}
