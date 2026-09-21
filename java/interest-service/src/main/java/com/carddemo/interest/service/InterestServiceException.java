package com.carddemo.interest.service;

import com.carddemo.interest.trace.Trace;

/** Java counterpart of 9999-ABEND-PROGRAM: an unrecoverable I/O or lookup failure. */
@Trace(program = "CBACT04C", paragraph = "9999-ABEND-PROGRAM", lines = "631-641")
public class InterestServiceException extends RuntimeException {
    public InterestServiceException(String message) {
        super(message);
    }
}
