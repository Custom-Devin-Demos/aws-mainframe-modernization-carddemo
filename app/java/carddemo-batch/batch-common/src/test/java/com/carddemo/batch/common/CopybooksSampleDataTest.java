package com.carddemo.batch.common;

import static org.assertj.core.api.Assertions.assertThat;

import com.carddemo.batch.common.record.CobolRecord;
import com.carddemo.batch.common.record.Copybooks;
import com.carddemo.batch.common.record.FixedWidthFile;
import com.carddemo.batch.common.record.KeyedFile;
import com.carddemo.batch.common.record.RecordLayout;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class CopybooksSampleDataTest {

    static Path sample(String name) {
        return Paths.get(System.getProperty("carddemo.sampleData", "../../../data/ASCII")).resolve(name);
    }

    @ParameterizedTest
    @CsvSource({
        "acctdata.txt, ACCOUNT, 300, 50",
        "carddata.txt, CARD, 150, 50",
        "cardxref.txt, XREF, 50, 50",
        "custdata.txt, CUSTOMER, 500, 50",
        "dailytran.txt, DALYTRAN, 350, 300",
        "tcatbal.txt, TCATBAL, 50, 50",
        "trancatg.txt, TRANCATG, 60, 18",
        "trantype.txt, TRANTYPE, 60, 7",
    })
    void everySampleFileParsesAndRoundTrips(String file, String layoutName, int length, int count) throws Exception {
        RecordLayout layout = switch (layoutName) {
            case "ACCOUNT" -> Copybooks.ACCOUNT_RECORD;
            case "CARD" -> Copybooks.CARD_RECORD;
            case "XREF" -> Copybooks.CARD_XREF_RECORD;
            case "CUSTOMER" -> Copybooks.CUSTOMER_RECORD;
            case "DALYTRAN" -> Copybooks.DALYTRAN_RECORD;
            case "TCATBAL" -> Copybooks.TRAN_CAT_BAL_RECORD;
            case "TRANCATG" -> Copybooks.TRAN_CAT_RECORD;
            default -> Copybooks.TRAN_TYPE_RECORD;
        };
        assertThat(layout.length()).isEqualTo(length);
        List<CobolRecord> recs = FixedWidthFile.read(sample(file), layout);
        assertThat(recs).hasSize(count);
        List<String> lines = Files.readAllLines(sample(file)).stream().map(l -> l.replace("\r", "")).filter(l -> !l.isBlank()).toList();
        for (int i = 0; i < recs.size(); i++) {
            String original = (lines.get(i) + " ".repeat(length)).substring(0, length);
            // FILLER is re-emitted as spaces, so compare only the data portion of each image.
            assertThat(stripFillers(layout, recs.get(i).toFixedWidth())).isEqualTo(stripFillers(layout, original));
        }
    }

    private static String stripFillers(RecordLayout layout, String image) {
        StringBuilder sb = new StringBuilder();
        int pos = 0;
        for (var f : layout.fields()) {
            if (!f.isFiller()) {
                sb.append(image, pos, pos + f.length());
            }
            pos += f.length();
        }
        return sb.toString();
    }

    @Test
    void accountFieldsDecode() {
        CobolRecord acct = FixedWidthFile.read(sample("acctdata.txt"), Copybooks.ACCOUNT_RECORD).get(0);
        assertThat(acct.getString("ACCT-ID")).isEqualTo("00000000001");
        assertThat(acct.getDecimal("ACCT-CURR-BAL")).isEqualByComparingTo("194.00");
        assertThat(acct.getDecimal("ACCT-CREDIT-LIMIT")).isEqualByComparingTo("2020.00");
        assertThat(acct.getString("ACCT-EXPIRAION-DATE")).isEqualTo("2025-05-20");
        // Sample data quirk: the "A000000000" group value sits in the ACCT-ADDR-ZIP columns; ACCT-GROUP-ID is blank.
        assertThat(acct.getTrimmed("ACCT-ADDR-ZIP")).isEqualTo("A000000000");
        assertThat(acct.getTrimmed("ACCT-GROUP-ID")).isEmpty();
    }

    @Test
    void keyedFileBehavesLikeKsds() {
        KeyedFile xref = KeyedFile.load(sample("cardxref.txt"), Copybooks.CARD_XREF_RECORD, "XREF-CARD-NUM");
        assertThat(xref.size()).isEqualTo(50);
        CobolRecord first = xref.inKeyOrder().iterator().next();
        assertThat(xref.read(first.getString("XREF-CARD-NUM"))).contains(first);
        assertThat(xref.read("9999999999999999")).isEmpty();
        assertThat(xref.write(first)).isEqualTo(KeyedFile.Status.DUPLICATE_KEY);

        KeyedFile tcat = KeyedFile.load(sample("tcatbal.txt"), Copybooks.TRAN_CAT_BAL_RECORD, "TRANCAT-ACCT-ID", "TRANCAT-TYPE-CD", "TRANCAT-CD");
        String key = tcat.key("1", "01", "1");
        assertThat(key).isEqualTo("00000000001010001");
        CobolRecord bal = tcat.read(key).orElseThrow();
        bal.set("TRAN-CAT-BAL", bal.getDecimal("TRAN-CAT-BAL").add(new java.math.BigDecimal("10.25")));
        assertThat(tcat.rewrite(bal)).isEqualTo(KeyedFile.Status.OK);
        assertThat(tcat.read(key).orElseThrow().getDecimal("TRAN-CAT-BAL")).isEqualByComparingTo("10.25");
    }
}
