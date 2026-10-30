package com.carddemo.batch.common;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.carddemo.batch.common.job.BatchLauncher;
import org.junit.jupiter.api.Test;

class BatchLauncherTest {

    @Test
    void requiresOutputRoot() {
        assertThatThrownBy(() -> BatchLauncher.launch(new String[] {"--job", "X"}))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("--output-root");
    }
}
