package com.carddemo.batch.cbact02c;

import static org.assertj.core.api.Assertions.assertThat;

import com.carddemo.batch.common.job.BatchJob;
import com.carddemo.batch.testkit.JobHarness;
import com.carddemo.batch.testkit.ParityAssertions;
import com.carddemo.batch.testkit.SampleData;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** CBACT02C on the real app/data/ASCII/carddata.txt vs the GnuCOBOL run of the original program. */
class SampleDataParityTest {

    private static final String SCENARIO = "sample-data";

    @TempDir
    static Path out;

    static JobHarness.Run run;

    @BeforeAll
    static void runJob() throws Exception {
        run = JobHarness.forJob(new Cbact02cJob(), out).input(Cbact02cJob.CARDFILE_DD, SampleData.file("carddata.txt")).run();
    }

    @Test
    void cardListingMatchesCobol() {
        ParityAssertions.assertParquetMatches(run.dataset(Cbact02cJob.CARD_LISTING), Fixtures.expected(SCENARIO, "card_listing.csv"));
    }

    @Test
    void sysoutDatasetMatchesCobol() {
        ParityAssertions.assertCsvMatches(run.report(Cbact02cJob.SYSOUT), Fixtures.expected(SCENARIO, "sysout.csv"));
    }

    @Test
    void returnCodeAndCounters() {
        assertThat(run.result().returnCode()).isZero();
        assertThat(run.result().counters()).containsExactly(
                org.assertj.core.api.Assertions.entry(Cbact02cJob.CARD_RECORDS_READ, 50L),
                org.assertj.core.api.Assertions.entry(Cbact02cJob.SYSOUT_LINES, 52L));
    }

    @Test
    void sysoutStreamHasStartEndAndFullRecordImages() {
        List<String> lines = run.sysout().lines().toList();
        assertThat(lines).hasSize(52);
        assertThat(lines.get(0)).isEqualTo("START OF EXECUTION OF PROGRAM CBACT02C");
        assertThat(lines.get(51)).isEqualTo("END OF EXECUTION OF PROGRAM CBACT02C");
        // DISPLAY CARD-RECORD writes the whole 150-byte area, trailing FILLER spaces included.
        assertThat(lines.subList(1, 51)).allSatisfy(l -> assertThat(l).hasSize(150));
        assertThat(lines.get(1)).startsWith("050002445376574000000000050747Aniya Von");
        assertThat(Fixtures.rtrimmed(run.sysout()))
                .isEqualTo(Fixtures.lines(Fixtures.expected(SCENARIO, "sysout.csv")).stream().skip(1)
                        .map(l -> l.substring(l.indexOf(',') + 1)).toList());
    }

    @Test
    void manifestRecordsReturnCodeCountersAndOutputs() throws Exception {
        JsonNode m = new ObjectMapper().readTree(run.manifest().toFile());
        assertThat(m.get("program").asText()).isEqualTo("CBACT02C");
        assertThat(m.get("return_code").asInt()).isZero();
        assertThat(m.get("counters").get(Cbact02cJob.CARD_RECORDS_READ).asLong()).isEqualTo(50);
        assertThat(m.get("outputs")).hasSize(2);
        assertThat(m.get("outputs").get(0).get("dataset").asText()).isEqualTo(Cbact02cJob.CARD_LISTING);
        assertThat(m.get("outputs").get(0).get("format").asText()).isEqualTo("PARQUET");
        assertThat(m.get("outputs").get(0).get("rows").asLong()).isEqualTo(50);
        assertThat(m.get("outputs").get(1).get("dataset").asText()).isEqualTo(Cbact02cJob.SYSOUT);
        assertThat(m.get("outputs").get(1).get("format").asText()).isEqualTo("CSV");
        assertThat(m.get("outputs").get(1).get("rows").asLong()).isEqualTo(52);
    }

    @Test
    void publishedSchemasEqualCheckedInContracts() throws Exception {
        for (String dataset : List.of(Cbact02cJob.CARD_LISTING, Cbact02cJob.SYSOUT)) {
            Path contract = Fixtures.MODULE_DIR.resolve("src/main/resources/schemas/" + dataset + ".schema.json");
            assertThat(Files.readString(run.schema(dataset), StandardCharsets.UTF_8))
                    .isEqualTo(Files.readString(contract, StandardCharsets.UTF_8));
        }
    }

    /** The KSDS browse is in key order; the sample unload happens to be sorted already (verified here). */
    @Test
    void sampleUnloadIsAlreadyInKeyOrder() {
        List<String> keys = Fixtures.lines(SampleData.file("carddata.txt")).stream().map(l -> l.substring(0, 16)).toList();
        assertThat(keys).hasSize(50).doesNotHaveDuplicates().isSorted();
    }

    @Test
    void isRegisteredAsServiceProvider() {
        assertThat(java.util.ServiceLoader.load(BatchJob.class).stream().map(p -> p.get().programId()))
                .containsExactly("CBACT02C");
    }
}
