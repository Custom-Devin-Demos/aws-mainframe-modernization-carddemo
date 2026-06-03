package com.carddemo.interest.service;

/**
 * Summary of a single interest calculation run.
 *
 * @param accountCount     number of distinct accounts processed
 * @param transactionCount number of interest transactions written
 */
public record InterestRunResult(int accountCount, int transactionCount) {
}
