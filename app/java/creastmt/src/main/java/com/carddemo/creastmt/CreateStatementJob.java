package com.carddemo.creastmt;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.logging.Logger;

/**
 * Java port of the CREASTMT job (CBSTM03A + CBSTM03B): one statement per card in the
 * cross-reference file, in plain text (STMTFILE) and HTML (HTMLFILE).
 */
public final class CreateStatementJob {

    private static final Logger LOG = Logger.getLogger(CreateStatementJob.class.getName());

    private final Path xrefFile;
    private final Path custFile;
    private final Path acctFile;
    private final Path trnxFile;

    public CreateStatementJob(Path xrefFile, Path custFile, Path acctFile, Path trnxFile) {
        this.xrefFile = xrefFile;
        this.custFile = custFile;
        this.acctFile = acctFile;
        this.trnxFile = trnxFile;
    }

    /** 1000-MAINLINE */
    public void run(Path stmtFile, Path htmlFile) throws IOException {
        TransactionTable transactions = TransactionTable.load(trnxFile);
        LOG.info(() -> "Loaded " + transactions.transactionCount() + " transactions for "
                + transactions.cardCount() + " cards from " + trnxFile);

        // XREFFILE is a KSDS read sequentially, i.e. in card-number order.
        List<CardXrefRecord> xrefs = new ArrayList<>(readRecords(xrefFile, CardXrefRecord::parse));
        xrefs.sort(Comparator.comparing(CardXrefRecord::cardNum));
        Map<Long, CustomerRecord> customers =
                index(readRecords(custFile, CustomerRecord::parse), CustomerRecord::custId);
        Map<Long, AccountRecord> accounts =
                index(readRecords(acctFile, AccountRecord::parse), AccountRecord::acctId);

        int statements = 0;
        try (StatementWriter writer = new StatementWriter(stmtFile, htmlFile)) {
            for (CardXrefRecord xref : xrefs) {
                CustomerRecord cust = lookup(customers, xref.custId(), "CUSTFILE");
                AccountRecord acct = lookup(accounts, xref.acctId(), "ACCTFILE");
                writer.writeStatement(cust, acct, transactions.forCard(xref.cardNum()));
                statements++;
            }
        }
        int written = statements;
        LOG.info(() -> "Wrote " + written + " statements to " + stmtFile + " and " + htmlFile);
    }

    /** 2000-CUSTFILE-GET / 3000-ACCTFILE-GET: keyed read; anything but status '00' abends. */
    private static <T> T lookup(Map<Long, T> file, long key, String ddName) {
        T record = file.get(key);
        if (record == null) {
            throw new IllegalStateException("ERROR READING " + ddName + ": record not found for key " + key);
        }
        return record;
    }

    private static <T> List<T> readRecords(Path file, Function<String, T> parser) throws IOException {
        List<T> records = new ArrayList<>();
        for (String line : Files.readAllLines(file, StandardCharsets.ISO_8859_1)) {
            records.add(parser.apply(line));
        }
        return records;
    }

    private static <T> Map<Long, T> index(List<T> records, Function<T, Long> key) {
        Map<Long, T> map = new HashMap<>();
        for (T record : records) {
            map.putIfAbsent(key.apply(record), record);
        }
        return map;
    }

    public static void main(String[] args) throws IOException {
        Map<String, String> opts = new LinkedHashMap<>();
        for (String arg : args) {
            int eq = arg.indexOf('=');
            if (!arg.startsWith("--") || eq < 0) {
                usage();
            }
            opts.put(arg.substring(2, eq), arg.substring(eq + 1));
        }
        for (String required : List.of("xref", "cust", "acct", "trnx", "stmt", "html")) {
            if (!opts.containsKey(required)) {
                usage();
            }
        }
        new CreateStatementJob(Path.of(opts.get("xref")), Path.of(opts.get("cust")),
                Path.of(opts.get("acct")), Path.of(opts.get("trnx")))
                .run(Path.of(opts.get("stmt")), Path.of(opts.get("html")));
    }

    private static void usage() {
        System.err.println("usage: CreateStatementJob --xref=<cardxref> --cust=<custdata> --acct=<acctdata>"
                + " --trnx=<transact> --stmt=<statement.txt> --html=<statement.html>");
        System.exit(2);
    }
}
