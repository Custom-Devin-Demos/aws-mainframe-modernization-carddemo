package com.carddemo.online;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class CardDemoOnlineApplication {

    public static void main(String[] args) {
        SpringApplication.run(CardDemoOnlineApplication.class, args);
    }
}
