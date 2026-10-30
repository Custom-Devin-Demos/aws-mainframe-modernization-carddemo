package com.carddemo.batch.cbtrn03c;

import static org.assertj.core.api.Assertions.assertThat;

import com.carddemo.batch.common.job.BatchJob;
import com.carddemo.batch.testkit.SchemaContract;
import java.util.ServiceLoader;
import org.junit.jupiter.api.Test;

class Cbtrn03cJobContractTest {

    @Test
    void schemaContractMatchesCheckedInFile() throws Exception {
        SchemaContract.assertMatches(Cbtrn03cJob.SCHEMA, Scenario.MODULE_DIR);
    }

    @Test
    void registeredAsBatchJobService() {
        assertThat(ServiceLoader.load(BatchJob.class).stream().map(p -> p.get().programId())).containsExactly("CBTRN03C");
    }
}
