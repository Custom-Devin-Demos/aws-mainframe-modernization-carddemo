package com.carddemo.batch.common.output;

import java.math.BigDecimal;

/** Canonical text form of a column value, shared by CSV outputs and the parity test fixtures. */
public final class Values {

    private Values() {
    }

    public static String canonical(Object v) {
        if (v == null) {
            return "";
        }
        if (v instanceof BigDecimal d) {
            return d.toPlainString();
        }
        return v.toString();
    }
}
