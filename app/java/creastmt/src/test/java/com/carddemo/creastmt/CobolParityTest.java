package com.carddemo.creastmt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Runs the original CBSTM03A/CBSTM03B under GnuCOBOL (parity/run-cobol.sh) and this port on the
 * same repo sample data, and requires both statement files to be byte-for-byte identical.
 * Fails (does not skip) when GnuCOBOL is unavailable.
 */
class CobolParityTest {

    private static Path root;
    private static Path cobolOut;
    private static Path javaOut;

    @BeforeAll
    static void runBothImplementations() throws Exception {
        root = Path.of(System.getProperty("carddemo.root", "../../..")).toAbsolutePath().normalize();
        Path parityDir = Path.of(System.getProperty("parity.dir", "target/parity")).toAbsolutePath();
        cobolOut = parityDir.resolve("cobol");
        javaOut = parityDir.resolve("java");

        Path script = root.resolve("app/java/creastmt/parity/run-cobol.sh");
        Path log = Files.createDirectories(parityDir).resolve("run-cobol.log");
        Process cobol = new ProcessBuilder("bash", script.toString(), cobolOut.toString())
                .redirectErrorStream(true).redirectOutput(log.toFile()).start();
        if (!cobol.waitFor(5, TimeUnit.MINUTES) || cobol.exitValue() != 0) {
            fail("GnuCOBOL run of CBSTM03A failed (is cobc installed?):\n" + Files.readString(log));
        }

        Files.createDirectories(javaOut);
        Path data = root.resolve("app/data/ASCII");
        new CreateStatementJob(data.resolve("cardxref.txt"), data.resolve("custdata.txt"),
                data.resolve("acctdata.txt"), data.resolve("dailytran.txt"))
                .run(javaOut.resolve("STATEMNT.PS"), javaOut.resolve("STATEMNT.HTML"));
    }

    @Test
    void textStatementMatchesCobol() throws IOException {
        assertIdentical("STATEMNT.PS", StatementWriter.STMT_LRECL);
    }

    @Test
    void htmlStatementMatchesCobol() throws IOException {
        assertIdentical("STATEMNT.HTML", StatementWriter.HTML_LRECL);
    }

    private static void assertIdentical(String name, int lrecl) throws IOException {
        byte[] expected = Files.readAllBytes(cobolOut.resolve(name));
        byte[] actual = Files.readAllBytes(javaOut.resolve(name));
        assertTrue(expected.length > 0, "COBOL produced an empty " + name);

        List<String> cobolLines = Files.readAllLines(cobolOut.resolve(name), StandardCharsets.ISO_8859_1);
        List<String> javaLines = Files.readAllLines(javaOut.resolve(name), StandardCharsets.ISO_8859_1);
        for (int i = 0; i < Math.min(cobolLines.size(), javaLines.size()); i++) {
            assertEquals(cobolLines.get(i), javaLines.get(i), name + " differs at record " + (i + 1));
        }
        assertEquals(cobolLines.size(), javaLines.size(), name + " record count");
        for (String line : javaLines) {
            assertEquals(lrecl, line.length(), name + " record length");
        }
        assertTrue(java.util.Arrays.equals(expected, actual), name + " is not byte-identical");
    }
}
