package com.carddemo.intcalc;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

/** Rule R11: record layouts match the CardDemo copybooks byte for byte. */
class RecordLayoutTest {

    @Test
    void parsesTranCatBalanceRecord() {
        TranCatBalance t = TranCatBalance.parse("000000001020100010000002507N                      ");
        assertEquals("00000000102", t.acctId());
        assertEquals("01", t.typeCode());
        assertEquals("0001", t.catCode());
        assertEquals(new BigDecimal("-250.75"), t.balance());
    }

    @Test
    void parsesShortAndCrlfTerminatedLines() {
        CardXref x = CardXref.parse("050002445376574000000005000000000050\r");
        assertEquals("0500024453765740", x.cardNum());
        assertEquals("00000000050", x.acctId());
        DisclosureGroup d = DisclosureGroup.parse("A00000000001000100150{");
        assertEquals("A000000000", d.groupId());
        assertEquals(new BigDecimal("15.00"), d.interestRate());
    }

    @Test
    void accountRewriteOnlyTouchesBalanceAndCycleFields() {
        Account a = TestData.account("00000000101", "1500.00", "120.50", "75.25", "GOLD");
        String before = a.toRecord();
        a.applyInterest(new BigDecimal("37.46"));
        String after = a.toRecord();
        assertEquals(300, after.length());
        assertEquals("00000015374F", after.substring(12, 24));
        assertEquals("00000000000{", after.substring(78, 90));
        assertEquals("00000000000{", after.substring(90, 102));
        assertEquals(before.substring(0, 12), after.substring(0, 12));
        assertEquals(before.substring(24, 78), after.substring(24, 78));
        assertEquals(before.substring(102), after.substring(102));
    }

    @Test
    void transactionRecordIs350BytesWithCopybookOffsets() {
        Transaction t = new Transaction("2022071800000001", "01", "0005", "System",
                "Int. for a/c 00000000101", new BigDecimal("20.56"), 0L, "", "", "",
                "4000000000000101", "2022-07-18-01.02.03.160000", "2022-07-18-01.02.03.160000");
        String r = t.toRecord();
        assertEquals(350, r.length());
        assertEquals("2022071800000001", r.substring(0, 16));
        assertEquals("010005", r.substring(16, 22));
        assertEquals("System    ", r.substring(22, 32));
        assertEquals("Int. for a/c 00000000101", r.substring(32, 132).trim());
        assertEquals("0000000205F", r.substring(132, 143));
        assertEquals("000000000", r.substring(143, 152));
        assertEquals("4000000000000101", r.substring(262, 278));
        assertEquals("2022-07-18-01.02.03.160000", r.substring(278, 304));
        assertEquals("2022-07-18-01.02.03.160000", r.substring(304, 330));
        assertEquals(" ".repeat(20), r.substring(330));
    }
}
