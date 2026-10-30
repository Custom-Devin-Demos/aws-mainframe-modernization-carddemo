package com.carddemo.batch.cbact02c;

import static org.assertj.core.api.Assertions.assertThat;

import com.carddemo.batch.common.job.BatchLauncher;
import com.carddemo.batch.testkit.SampleData;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The container entry point as AWS Batch invokes it (deploy/cbact02c-job-definition.json). */
class LauncherTest {

    @TempDir
    Path out;

    @Test
    void sampleRunExitsZeroAndPublishesCuratedLayout() {
        int exit = BatchLauncher.launch(new String[] {"--input", "CARDFILE=" + SampleData.file("carddata.txt"),
            "--output-root", out.toString(), "--run-date", "2022-07-06", "--run-id", "local"});
        assertThat(exit).isZero();
        Path base = out.resolve("curated/cbact02c");
        assertThat(base.resolve("card_listing/run_date=2022-07-06/run_id=local/part-00000.parquet")).isRegularFile();
        assertThat(base.resolve("card_listing/run_date=2022-07-06/run_id=local/_schema.json")).isRegularFile();
        assertThat(base.resolve("sysout/run_date=2022-07-06/run_id=local/part-00000.csv")).isRegularFile();
        assertThat(base.resolve("_runs/run_date=2022-07-06/run_id=local/_manifest.json")).isRegularFile();
    }

    @Test
    void abendExits99AndPublishesNothing() throws Exception {
        int exit = BatchLauncher.launch(new String[] {"--input", "CARDFILE=" + out.resolve("absent.txt"),
            "--output-root", out.toString(), "--run-date", "2022-07-06", "--run-id", "local"});
        assertThat(exit).isEqualTo(BatchLauncher.ABEND_EXIT);
        try (var files = Files.walk(out)) {
            assertThat(files.filter(Files::isRegularFile)).isEmpty();
        }
    }
}
