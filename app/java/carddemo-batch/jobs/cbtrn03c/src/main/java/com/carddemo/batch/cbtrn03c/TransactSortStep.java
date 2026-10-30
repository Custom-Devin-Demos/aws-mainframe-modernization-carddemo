package com.carddemo.batch.cbtrn03c;

import com.carddemo.batch.common.record.CobolRecord;
import com.carddemo.batch.common.record.Copybooks;
import com.carddemo.batch.common.record.KeyedFile;
import java.math.BigInteger;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * TRANREPT STEP05R folded into the job (design section 2.2): IDCAMS REPRO of the TRANSACT KSDS, then
 * DFSORT {@code SORT FIELDS=(TRAN-CARD-NUM,A)} with
 * {@code INCLUDE COND=(TRAN-PROC-DT,GE,PARM-START-DATE,AND,TRAN-PROC-DT,LE,PARM-END-DATE)}.
 *
 * <p>Sort stability: DFSORT's default is EQUALS=NO, which leaves the order of records with equal
 * card numbers unspecified. This step pins it to EQUALS: equal keys keep their REPRO (TRAN-ID) order.
 */
public final class TransactSortStep {

    static final String POSITIVE = "{ABCDEFGHI";
    static final String NEGATIVE = "}JKLMNOPQR";

    /** Records unloaded by REPRO, and the SORTOUT records passed to CBTRN03C as TRANFILE. */
    public record Result(List<CobolRecord> unloaded, List<CobolRecord> selected) {

        public long omitted() {
            return unloaded.size() - selected.size();
        }
    }

    private TransactSortStep() {
    }

    public static Result run(Path transactUnload, String startDate, String endDate) {
        List<CobolRecord> unloaded = repro(transactUnload);
        List<CobolRecord> selected = new ArrayList<>();
        for (CobolRecord rec : unloaded) {
            if (include(rec, startDate, endDate)) {
                selected.add(rec);
            }
        }
        // List.sort is a stable merge sort: SORT ... EQUALS.
        selected.sort(Comparator.comparing(r -> zonedKey(r.getString("TRAN-CARD-NUM"))));
        return new Result(unloaded, selected);
    }

    /** REPRO from the KSDS: ascending TRAN-ID order; a KSDS cannot hold duplicate keys. */
    static List<CobolRecord> repro(Path transactUnload) {
        return new ArrayList<>(KeyedFile.load(transactUnload, Copybooks.TRAN_RECORD, "TRAN-ID").inKeyOrder());
    }

    /** TRAN-PROC-DT,305,10,CH compared with the two date constants (inclusive). */
    static boolean include(CobolRecord rec, String startDate, String endDate) {
        String procDate = rec.getString("TRAN-PROC-TS").substring(0, 10);
        return procDate.compareTo(startDate) >= 0 && procDate.compareTo(endDate) <= 0;
    }

    /**
     * Value of a ZD sort field: the last byte may carry an overpunched sign; other bytes contribute
     * their low-order nibble, as DFSORT does for zoned data.
     */
    static BigInteger zonedKey(String raw) {
        StringBuilder digits = new StringBuilder(raw.length());
        boolean negative = false;
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            boolean last = i == raw.length() - 1;
            if (c >= '0' && c <= '9') {
                digits.append(c);
            } else if (last && POSITIVE.indexOf(c) >= 0) {
                digits.append(POSITIVE.indexOf(c));
            } else if (last && NEGATIVE.indexOf(c) >= 0) {
                digits.append(NEGATIVE.indexOf(c));
                negative = true;
            } else {
                digits.append((c & 0x0F) % 10);
            }
        }
        BigInteger v = new BigInteger(digits.toString());
        return negative ? v.negate() : v;
    }
}
