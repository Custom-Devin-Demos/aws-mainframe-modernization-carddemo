package com.carddemo.batch.cbtrn03c;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.carddemo.batch.common.record.CobolRecord;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** BR-CBTRN03C-01/02: the folded-in REPRO + SORT/INCLUDE (TRANREPT STEP05R). */
class TransactSortStepTest {

    static final Path INPUT = new Scenario("presort-filter-order").input.resolve("tranfile.txt");

    @Test
    void reproUnloadsInTranIdOrder() {
        List<String> ids = ids(TransactSortStep.repro(INPUT));
        assertThat(ids).containsExactly("5", "10", "15", "20", "30", "40", "50", "60", "70");
    }

    @Test
    void includeIsInclusiveOnTheDatePartOfProcTs() {
        TransactSortStep.Result r = TransactSortStep.run(INPUT, "2022-01-01", "2022-07-06");
        assertThat(r.unloaded()).hasSize(9);
        assertThat(r.omitted()).isEqualTo(3);
        assertThat(ids(r.selected())).doesNotContain("30", "60", "70").contains("20", "40");
    }

    @Test
    void sortsByCardNumberKeepingEqualKeysInReproOrder() {
        TransactSortStep.Result r = TransactSortStep.run(INPUT, "2022-01-01", "2022-07-06");
        // C1 (0500...), C2 (0683...), C3 (0923...); within a card ascending TRAN-ID = EQUALS on the REPRO order.
        assertThat(ids(r.selected())).containsExactly("20", "40", "15", "5", "10", "50");
    }

    @Test
    void zonedKeyHonoursOverpunchedSign() {
        assertThat(TransactSortStep.zonedKey("0000000000000012")).isEqualTo(BigInteger.valueOf(12));
        assertThat(TransactSortStep.zonedKey("000000000000001B")).isEqualTo(BigInteger.valueOf(12));
        assertThat(TransactSortStep.zonedKey("000000000000001K")).isEqualTo(BigInteger.valueOf(-12));
        assertThat(TransactSortStep.zonedKey("                ")).isEqualTo(BigInteger.ZERO);
    }

    @Test
    void duplicateTranIdIsRejectedLikeAKsds(@TempDir Path tmp) throws Exception {
        String rec = Files.readAllLines(INPUT).get(0);
        Path dup = tmp.resolve("dup.txt");
        Files.write(dup, List.of(rec, rec));
        assertThatThrownBy(() -> TransactSortStep.repro(dup)).hasMessageContaining("duplicate key");
    }

    private static List<String> ids(List<CobolRecord> recs) {
        return recs.stream().map(r -> String.valueOf(Long.parseLong(r.getString("TRAN-ID")))).toList();
    }
}
