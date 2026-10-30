package com.carddemo.intcalc;

import java.math.BigDecimal;

/** DIS-GROUP-RECORD (copybook CVTRA02Y, 50 bytes). */
public record DisclosureGroup(String groupId, String typeCode, String catCode, BigDecimal interestRate) {

    public static final int LENGTH = 50;

    public static DisclosureGroup parse(String line) {
        String r = FixedWidth.normalise(line, LENGTH);
        return new DisclosureGroup(
                FixedWidth.field(r, 0, 10),
                FixedWidth.field(r, 10, 2),
                FixedWidth.field(r, 12, 4),
                CobolNumeric.parseSigned(FixedWidth.field(r, 16, 6), 2));
    }

    /** DIS-GROUP-KEY (group id is PIC X(10), so it is compared space-padded). */
    public static String key(String groupId, String typeCode, String catCode) {
        return FixedWidth.alpha(groupId, 10) + typeCode + catCode;
    }

    public String key() {
        return key(groupId, typeCode, catCode);
    }
}
