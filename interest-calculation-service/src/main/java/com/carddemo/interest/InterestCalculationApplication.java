package com.carddemo.interest;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Spring Boot entry point for the interest calculation service.
 *
 * <p>This service is a Java/Spring reimplementation of the COBOL batch program
 * {@code app/cbl/CBACT04C.cbl} (CardDemo), which computes monthly interest for
 * credit card accounts based on transaction category balances and disclosure
 * group interest rates.
 */
@SpringBootApplication
public class InterestCalculationApplication {

    public static void main(String[] args) {
        SpringApplication.run(InterestCalculationApplication.class, args);
    }
}
