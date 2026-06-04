package com.carddemo.interest;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Spring Boot entry point for the interest-service.
 *
 * <p>Java/Spring reimplementation of the COBOL batch program {@code CBACT04C.cbl},
 * which computes monthly interest on transaction category balances and posts the
 * resulting interest transactions, then rolls the interest into each account balance.
 */
@SpringBootApplication
public class InterestServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(InterestServiceApplication.class, args);
    }
}
