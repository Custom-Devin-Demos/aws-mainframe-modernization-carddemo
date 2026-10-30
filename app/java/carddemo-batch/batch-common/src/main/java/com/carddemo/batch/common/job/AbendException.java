package com.carddemo.batch.common.job;

/** Equivalent of {@code CALL 'CEE3ABD'}: stops the job, no outputs are published. */
public class AbendException extends RuntimeException {

    private final int abendCode;

    public AbendException(int abendCode, String message) {
        super(message);
        this.abendCode = abendCode;
    }

    public int abendCode() {
        return abendCode;
    }
}
