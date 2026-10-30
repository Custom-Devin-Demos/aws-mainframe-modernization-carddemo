package com.carddemo.intcalc;

/** Raised where CBACT04C would DISPLAY an error and abend via 9999-ABEND-PROGRAM. */
public class IntCalcException extends RuntimeException {

    public IntCalcException(String message) {
        super(message);
    }
}
