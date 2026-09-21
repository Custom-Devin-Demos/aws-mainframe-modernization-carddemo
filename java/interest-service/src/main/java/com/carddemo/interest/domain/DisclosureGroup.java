package com.carddemo.interest.domain;

import com.carddemo.interest.trace.Trace;
import java.math.BigDecimal;

/**
 * DIS-GROUP-RECORD (copybook CVTRA02Y, RECLN 50).
 *
 * <pre>
 * 05 DIS-GROUP-KEY
 *    10 DIS-ACCT-GROUP-ID  PIC X(10)
 *    10 DIS-TRAN-TYPE-CD   PIC X(02)
 *    10 DIS-TRAN-CAT-CD    PIC 9(04)
 * 05 DIS-INT-RATE          PIC S9(04)V99
 * 05 FILLER                PIC X(28)
 * </pre>
 *
 * @param groupId DIS-ACCT-GROUP-ID, 10 chars (space padded)
 * @param typeCd  DIS-TRAN-TYPE-CD, 2 chars
 * @param catCd   DIS-TRAN-CAT-CD, 4 digits
 * @param intRate DIS-INT-RATE, scale 2 (annual percentage rate)
 */
@Trace(copybook = "CVTRA02Y", lines = "4-10")
public record DisclosureGroup(String groupId, String typeCd, String catCd, BigDecimal intRate) {

    public static final int RECORD_LENGTH = 50;

    /** Group id used for the fallback lookup in 1200-GET-INTEREST-RATE (CBACT04C line 437). */
    public static final String DEFAULT_GROUP_ID = "DEFAULT";

    public DisclosureGroup {
        intRate = CobolFields.scale2(intRate, "intRate");
    }

    @Trace(copybook = "CVTRA02Y", lines = "4-10")
    public static DisclosureGroup fromRecord(String record) {
        String value = CobolFields.checkLength(record, RECORD_LENGTH);
        return new DisclosureGroup(
                CobolFields.picX(value, 0, 10),
                CobolFields.picX(value, 10, 2),
                CobolFields.pic9(value, 12, 4),
                CobolFields.picS9V99(value, 16, 6));
    }

    @Trace(copybook = "CVTRA02Y", lines = "4-10")
    public String toRecord() {
        String result = CobolFields.formatX(groupId, 10)
                + CobolFields.formatX(typeCd, 2)
                + CobolFields.format9(catCd, 4)
                + CobolFields.formatS9V99(intRate, 6)
                + " ".repeat(28);
        if (result.length() != RECORD_LENGTH) {
            throw new IllegalStateException("record length " + result.length() + ", expected " + RECORD_LENGTH);
        }
        return result;
    }
}
