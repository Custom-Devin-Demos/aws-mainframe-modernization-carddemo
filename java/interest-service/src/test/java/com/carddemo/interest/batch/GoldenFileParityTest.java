package com.carddemo.interest.batch;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.test.JobLauncherTestUtils;
import org.springframework.batch.test.context.SpringBatchTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Byte-for-byte parity against the output of the COBOL program itself. The {@code expected/} files
 * under {@code src/test/resources/fixtures/<scenario>} were produced by running CBACT04C under
 * GnuCOBOL (see fixtures/README.md and tools/generate.sh). The only columns that differ between
 * runs are TRAN-ORIG-TS / TRAN-PROC-TS (SYSTRAN bytes 279-330, {@code Z-GET-DB2-FORMAT-TIMESTAMP}),
 * which are checked for format only.
 */
@SpringBootTest
@SpringBatchTest
class GoldenFileParityTest {

    static final Path FIXTURES = Path.of("src", "test", "resources", "fixtures");
    static final String PARM_DATE = "2022071800";
    static final int TS_START = 278;
    static final int TS_END = 330;
    static final Pattern DB2_TS = Pattern.compile("\\d{4}-\\d{2}-\\d{2}-\\d{2}\\.\\d{2}\\.\\d{2}\\.\\d{6}");

    @Autowired
    JobLauncherTestUtils jobLauncherTestUtils;

    @TempDir
    Path work;

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"sample-data", "nonzero-balances", "single-account"})
    void cbact04c_outputMatchesCobolGoldenFiles(String scenario) throws Exception {
        Path input = FIXTURES.resolve(scenario).resolve("input");
        Path expected = FIXTURES.resolve(scenario).resolve("expected");
        Path acct = Files.copy(input.resolve("acctdata.txt"), work.resolve("acctdata.txt"));
        Path tran = work.resolve("systran.txt");

        JobExecution execution = jobLauncherTestUtils.launchJob(new JobParametersBuilder()
                .addString(InterestJobConfig.PARAM_RUN_DATE, PARM_DATE)
                .addString(InterestJobConfig.PARAM_TCATBAL_FILE, input.resolve("tcatbal.txt").toString())
                .addString(InterestJobConfig.PARAM_XREF_FILE, input.resolve("cardxref.txt").toString())
                .addString(InterestJobConfig.PARAM_DISCGRP_FILE, input.resolve("discgrp.txt").toString())
                .addString(InterestJobConfig.PARAM_ACCT_FILE, acct.toString())
                .addString(InterestJobConfig.PARAM_TRAN_FILE, tran.toString())
                .addLong("ts", System.nanoTime())
                .toJobParameters());
        assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);

        List<String> expectedTran = lines(expected.resolve("systran.txt"));
        List<String> actualTran = lines(tran);
        assertThat(actualTran).hasSameSizeAs(expectedTran);
        for (int i = 0; i < expectedTran.size(); i++) {
            String exp = expectedTran.get(i);
            String act = actualTran.get(i);
            assertThat(act).as("SYSTRAN record %d (excluding timestamps)", i + 1)
                    .hasSize(exp.length());
            assertThat(act.substring(0, TS_START)).isEqualTo(exp.substring(0, TS_START));
            assertThat(act.substring(TS_END)).isEqualTo(exp.substring(TS_END));
            String origTs = act.substring(TS_START, TS_START + 26);
            String procTs = act.substring(TS_START + 26, TS_END);
            assertThat(origTs).matches(DB2_TS).isEqualTo(procTs);
        }

        assertThat(lines(acct)).as("ACCTFILE after 1050-UPDATE-ACCOUNT")
                .containsExactlyElementsOf(lines(expected.resolve("acctdata.txt")));
    }

    static List<String> lines(Path file) throws IOException {
        return Files.readAllLines(file, StandardCharsets.UTF_8);
    }
}
