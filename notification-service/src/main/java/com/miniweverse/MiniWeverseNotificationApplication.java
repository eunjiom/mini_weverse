package com.miniweverse;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.kafka.annotation.EnableKafka;

@EnableKafka
@ConfigurationPropertiesScan
@SpringBootApplication
public class MiniWeverseNotificationApplication {

    public static void main(String[] args) {
        SpringApplication.run(MiniWeverseNotificationApplication.class, args);
    }

}
