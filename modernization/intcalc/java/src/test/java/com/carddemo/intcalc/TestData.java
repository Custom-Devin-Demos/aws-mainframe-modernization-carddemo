package com.carddemo.intcalc;

import java.math.BigDecimal;

/** Builders for fixed-width test records in CardDemo copybook layouts. */
final class TestData {

    private TestData() {
    }

    static TranCatBalance tcat(String acct, String type, int cat, String balance) {
        return new TranCatBalance(acct, type, String.format("%04d", cat), new BigDecimal(balance));
    }

    static DisclosureGroup rate(String group, String type, int cat, String rate) {
        return new DisclosureGroup(FixedWidth.alpha(group, 10), type, String.format("%04d", cat),
                new BigDecimal(rate));
    }

    static CardXref xref(String acct, String card) {
        return new CardXref(card, "900000001", acct);
    }

    static Account account(String acct, String balance, String cycCredit, String cycDebit, String group) {
        String record = acct + "Y"
                + CobolNumeric.formatSigned(new BigDecimal(balance), 10, 2)
                + CobolNumeric.formatSigned(new BigDecimal("5000.00"), 10, 2)
                + CobolNumeric.formatSigned(new BigDecimal("1000.00"), 10, 2)
                + "2020-01-012030-12-312025-01-01"
                + CobolNumeric.formatSigned(new BigDecimal(cycCredit), 10, 2)
                + CobolNumeric.formatSigned(new BigDecimal(cycDebit), 10, 2)
                + FixedWidth.alpha("94105", 10)
                + FixedWidth.alpha(group, 10);
        return Account.parse(record);
    }
}
