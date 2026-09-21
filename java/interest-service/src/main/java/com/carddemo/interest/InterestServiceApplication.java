package com.carddemo.interest;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class InterestServiceApplication {
    public static void main(String[] args) {
        System.exit(SpringApplication.exit(SpringApplication.run(InterestServiceApplication.class, args)));
    }
}
