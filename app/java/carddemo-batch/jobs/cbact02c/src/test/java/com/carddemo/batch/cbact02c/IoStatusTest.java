package com.carddemo.batch.cbact02c;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/** 9910-DISPLAY-IO-STATUS vs the GnuCOBOL run of the extracted paragraph (ASCII) and its EBCDIC derivation. */
class IoStatusTest {

    static Stream<Arguments> ascii() {
        return table("ascii.csv");
    }

    static Stream<Arguments> ebcdic() {
        return table("ebcdic.csv");
    }

    private static Stream<Arguments> table(String file) {
        return Fixtures.lines(Fixtures.expected("io-status", file)).stream().skip(1)
                .map(l -> l.split(",", 2)).map(p -> Arguments.of(p[0], p[1]));
    }

    @ParameterizedTest(name = "{0} -> {1}")
    @MethodSource("ascii")
    void matchesGnuCobolUnderAsciiCodePage(String status, String line) {
        assertThat("FILE STATUS IS: NNNN" + IoStatus.ioStatus04(status, StandardCharsets.US_ASCII)).isEqualTo(line);
    }

    @ParameterizedTest(name = "{0} -> {1}")
    @MethodSource("ebcdic")
    void jobUsesEbcdicCodePageLikeZos(String status, String line) {
        assertThat(IoStatus.displayLine(status)).isEqualTo(line);
    }

    @Test
    void rejectsStatusThatIsNotTwoCharacters() {
        assertThatThrownBy(() -> IoStatus.displayLine("3")).isInstanceOf(IllegalArgumentException.class);
    }
}
