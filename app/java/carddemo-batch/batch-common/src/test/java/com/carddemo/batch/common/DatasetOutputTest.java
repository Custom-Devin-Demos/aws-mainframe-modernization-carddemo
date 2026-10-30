package com.carddemo.batch.common;

import static org.assertj.core.api.Assertions.assertThat;

import com.carddemo.batch.common.job.BatchJob;
import com.carddemo.batch.common.job.JobContext;
import com.carddemo.batch.common.job.JobResult;
import com.carddemo.batch.common.job.JobRunner;
import com.carddemo.batch.common.output.DatasetWriter;
import com.carddemo.batch.common.output.OutputFormat;
import com.carddemo.batch.common.record.Copybooks;
import com.carddemo.batch.common.record.FixedWidthFile;
import com.carddemo.batch.common.schema.DatasetSchema;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DatasetOutputTest {

    @TempDir
    Path out;

    static final DatasetSchema ACCOUNTS = DatasetSchema.fromLayout("account_master", "Account master", Copybooks.ACCOUNT_RECORD);

    static class CopyAccounts implements BatchJob {
        @Override
        public String programId() {
            return "TESTJOB";
        }

        @Override
        public JobResult run(JobContext ctx) {
            long n = 0;
            try (DatasetWriter w = ctx.openDataset("account_master", ACCOUNTS, OutputFormat.PARQUET);
                 DatasetWriter csv = ctx.openDataset("account_report", ACCOUNTS, OutputFormat.CSV)) {
                for (var rec : FixedWidthFile.read(ctx.input("ACCTFILE"), Copybooks.ACCOUNT_RECORD)) {
                    w.write(rec);
                    csv.write(rec);
                    n++;
                }
            }
            return JobResult.ok(Map.of("records", n));
        }
    }

    @Test
    void writesParquetCsvSchemaAndManifestUsingNamingConvention() throws Exception {
        JobContext ctx = JobContext.builder("TESTJOB").runDate(LocalDate.of(2022, 7, 6)).runId("r1").stagingDir(out)
                .input("ACCTFILE", CopybooksSampleDataTest.sample("acctdata.txt")).build();
        JobResult result = JobRunner.run(new CopyAccounts(), ctx);

        assertThat(result.counters()).containsEntry("records", 50L);
        Path dir = out.resolve("curated/testjob/account_master/run_date=2022-07-06/run_id=r1");
        assertThat(dir.resolve("part-00000.parquet")).exists();
        assertThat(Files.readString(dir.resolve("_schema.json"))).contains("\"name\" : \"acct_curr_bal\"", "decimal(12,2)", "\"cobol_field\" : \"ACCT-CURR-BAL\"");
        Path csv = out.resolve("curated/testjob/account_report/run_date=2022-07-06/run_id=r1/part-00000.csv");
        var lines = Files.readAllLines(csv);
        assertThat(lines).hasSize(51);
        assertThat(lines.get(0)).startsWith("acct_id,acct_active_status,acct_curr_bal,");
        assertThat(lines.get(1)).startsWith("00000000001,Y,194.00,2020.00,1020.00,2014-11-20,");
        String manifest = Files.readString(out.resolve("curated/testjob/_runs/run_date=2022-07-06/run_id=r1/_manifest.json"));
        assertThat(manifest).contains("\"return_code\" : 0", "\"rows\" : 50", "account_master");
    }
}
