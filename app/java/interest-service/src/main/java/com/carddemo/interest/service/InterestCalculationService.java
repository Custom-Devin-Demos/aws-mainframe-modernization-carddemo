package com.carddemo.interest.service;

import java.time.LocalDate;

import org.springframework.stereotype.Service;

/**
 * Orchestrates the monthly interest calculation, mirroring the main
 * {@code PROCEDURE DIVISION} loop of the COBOL program {@code CBACT04C.cbl}.
 *
 * <p>NOTE: This is a scaffold skeleton. The full account-break orchestration
 * (rate lookup -> compute interest -> write transaction -> update account) is
 * implemented in the sequential integration step once the JPA entities,
 * repositories, {@code InterestCalculator}, and {@code InterestRateResolver}
 * are available.
 */
@Service
public class InterestCalculationService {

    /**
     * Run the interest calculation across all accounts.
     *
     * @param processingDate the processing/run date (COBOL {@code PARM-DATE}),
     *                        used to build transaction ids and timestamps.
     */
    public void run(LocalDate processingDate) {
        // TODO: Implemented in the sequential integration step.
        throw new UnsupportedOperationException("InterestCalculationService.run not yet implemented");
    }

    /**
     * Run the interest calculation for a single partition: the accounts whose id
     * falls within [{@code lowAccountId}, {@code highAccountId}] inclusive. Used by
     * the Spring Batch partitioned step so independent account ranges run in
     * parallel.
     *
     * @param processingDate the processing/run date.
     * @param lowAccountId   inclusive lower bound of the account id range.
     * @param highAccountId  inclusive upper bound of the account id range.
     */
    public void runForAccountRange(LocalDate processingDate, long lowAccountId, long highAccountId) {
        // TODO: Implemented in the sequential integration step.
        throw new UnsupportedOperationException(
                "InterestCalculationService.runForAccountRange not yet implemented");
    }
}
