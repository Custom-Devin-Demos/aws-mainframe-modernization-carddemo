package com.carddemo.creastmt;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Replaces CREASTMT STEP010 (SORT/OUTREC), STEP020 (REPRO into TRXFL KSDS) and CBSTM03A's
 * 8100-TRNXFILE-OPEN / 8500-READTRNX-READ, which load every transaction into the
 * WS-CARD-TBL / WS-TRAN-TBL arrays grouped by card number.
 */
final class TransactionTable {

    private final Map<String, List<TransactionRecord>> byCard;
    private final int size;

    private TransactionTable(Map<String, List<TransactionRecord>> byCard, int size) {
        this.byCard = byCard;
        this.size = size;
    }

    /** Loads TRANSACT-layout (CVTRA05Y) records. */
    static TransactionTable load(Path transactFile) throws IOException {
        List<String> rekeyed = new ArrayList<>();
        for (String line : Files.readAllLines(transactFile, StandardCharsets.ISO_8859_1)) {
            rekeyed.add(rekey(line));
        }
        // SORT FIELDS=(263,16,CH,A,1,16,CH,A): after OUTREC that is the leading 32-byte key.
        rekeyed.sort((a, b) -> a.substring(0, 32).compareTo(b.substring(0, 32)));

        Map<String, List<TransactionRecord>> byCard = new TreeMap<>();
        for (String record : rekeyed) {
            TransactionRecord trnx = TransactionRecord.parse(record);
            byCard.computeIfAbsent(trnx.cardNum(), k -> new ArrayList<>()).add(trnx);
        }
        return new TransactionTable(byCard, rekeyed.size());
    }

    /** OUTREC FIELDS=(1:263,16,17:1,262,279:279,50) into an LRECL 350 record. */
    static String rekey(String transactRecord) {
        String r = Cobol.pic(transactRecord, TransactionRecord.LENGTH);
        return Cobol.pic(r.substring(262, 278) + r.substring(0, 262) + r.substring(278, 328),
                TransactionRecord.LENGTH);
    }

    /** 4000-TRNXFILE-GET lookup: the card's transactions in transaction-id order. */
    List<TransactionRecord> forCard(String cardNum) {
        return Collections.unmodifiableList(byCard.getOrDefault(cardNum, List.of()));
    }

    int cardCount() {
        return byCard.size();
    }

    int transactionCount() {
        return size;
    }
}
