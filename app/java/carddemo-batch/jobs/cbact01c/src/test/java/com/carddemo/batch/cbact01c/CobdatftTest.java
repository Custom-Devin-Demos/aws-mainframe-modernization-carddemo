package com.carddemo.batch.cbact01c;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

/** COBDATFT port; expectations follow app/asm/COBDATFT.asm instruction by instruction. */
class CobdatftTest {

    private static Codatecn rec(char type, String in, char outType, String previousOut) {
        Codatecn r = new Codatecn();
        r.type = type;
        r.moveToInpDate(in);
        r.outType = outType;
        r.outDate = Codatecn.fit(previousOut, Codatecn.DATE_LENGTH);
        return r;
    }

    @ParameterizedTest(name = "VALIDIN2 [{0}] -> [{1}]")
    @CsvSource(delimiter = '|', value = {
            "2025-05-20|20250520",
            "2025/05/20|20250520",
            "20250520|202552",
            "' 025-05-20'|' 0250520'",
            "25-05-2020|25-0-220",
            "''|''"})
    void type2ToType2MovesBytes1to4_6to7_9to10(String in, String out) {
        Codatecn r = rec('2', in, '2', "");
        assertThat(Cobdatft.call(r)).isZero();
        assertThat(Cbact01cJob.rtrim(r.outDate)).isEqualTo(out);
        assertThat(r.errorMsg).isBlank();
    }

    @Test
    void type2LeavesOutputBytes9to20Untouched() {
        Codatecn r = rec('2', "2025-05-20", '2', "XXXXXXXXYYYYYYYYYYYY");
        Cobdatft.call(r);
        assertThat(r.outDate).isEqualTo("20250520YYYYYYYYYYYY");
    }

    @Test
    void type1ToType1InsertsDashes() {
        Codatecn r = rec('1', "20250520", '1', "");
        Cobdatft.call(r);
        assertThat(Cbact01cJob.rtrim(r.outDate)).isEqualTo("2025-05-20");
        assertThat(r.errorMsg).isBlank();
    }

    @ParameterizedTest(name = "GOTOERR type={0} in=[{1}] outtype={2}")
    @CsvSource(delimiter = '|', value = {
            "2|2025-05-20|1",
            "1|20250520|2",
            "1|2025-05-20|1",
            "3|2025-05-20|2",
            "' '|2025-05-20|2"})
    void invalidCombinationsSetErrorMessageAndKeepOutput(char type, String in, char outType) {
        Codatecn r = rec(type, in, outType, "PREVIOUS");
        assertThat(Cobdatft.call(r)).isZero();
        assertThat(r.errorMsg).isEqualTo(String.format("%-38s", "INVALID INPUT"));
        assertThat(r.outDate).isEqualTo(Codatecn.fit("PREVIOUS", 20));
    }
}
