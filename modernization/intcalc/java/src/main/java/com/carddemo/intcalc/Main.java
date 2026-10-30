package com.carddemo.intcalc;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Command-line entry point (replacement for JCL INTCALC STEP15).
 *
 * <pre>
 * java -jar intcalc.jar --input-dir DIR --output-dir DIR [--parm-date 2022071800]
 *      [--processing-ts 2022-07-18T01:02:03.16] [--update-last-account]
 * </pre>
 *
 * Inputs (line-sequential ASCII extracts, CardDemo copybook layouts): tcatbal.txt,
 * cardxref.txt, acctdata.txt, discgrp.txt. Outputs: transact.dat (fixed 350-byte TRAN-RECORDs,
 * the SYSTRAN(+1) GDG) and acctdata.dat (fixed 300-byte ACCOUNT-RECORDs after rewrite).
 */
public final class Main {

    static final int EXIT_OK = 0;
    static final int EXIT_USAGE = 8;
    static final int EXIT_ABEND = 16;

    private Main() {
    }

    public static void main(String[] args) {
        System.exit(run(args));
    }

    static int run(String[] args) {
        Map<String, String> opts;
        try {
            opts = parseArgs(args);
        } catch (IllegalArgumentException e) {
            System.err.println(e.getMessage());
            System.err.println("usage: --input-dir DIR --output-dir DIR [--parm-date X(10)] "
                    + "[--processing-ts yyyy-MM-ddTHH:mm:ss[.SS]] [--update-last-account]");
            return EXIT_USAGE;
        }
        try {
            Path in = Path.of(opts.get("--input-dir"));
            Path out = Path.of(opts.get("--output-dir"));
            LocalDateTime ts = opts.containsKey("--processing-ts")
                    ? LocalDateTime.parse(opts.get("--processing-ts"))
                    : LocalDateTime.now();
            JobConfig config = new JobConfig(
                    opts.getOrDefault("--parm-date", "2022071800"),
                    ts,
                    opts.containsKey("--update-last-account"));

            InterestCalculationJob.Result result = new InterestCalculationJob(config, System.out).run(
                    read(in.resolve("tcatbal.txt"), TranCatBalance::parse),
                    read(in.resolve("cardxref.txt"), CardXref::parse),
                    read(in.resolve("acctdata.txt"), Account::parse),
                    read(in.resolve("discgrp.txt"), DisclosureGroup::parse));

            Files.createDirectories(out);
            write(out.resolve("transact.dat"),
                    result.transactions().stream().map(Transaction::toRecord).toList());
            write(out.resolve("acctdata.dat"),
                    result.accounts().stream().map(Account::toRecord).toList());
            return EXIT_OK;
        } catch (IntCalcException e) {
            System.out.println(e.getMessage());
            System.out.println("ABENDING PROGRAM");
            return EXIT_ABEND;
        } catch (IOException | RuntimeException e) {
            System.err.println("ERROR: " + e);
            return EXIT_ABEND;
        }
    }

    private static Map<String, String> parseArgs(String[] args) {
        Map<String, String> opts = new HashMap<>();
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--update-last-account" -> opts.put(args[i], "true");
                case "--input-dir", "--output-dir", "--parm-date", "--processing-ts" -> {
                    if (i + 1 >= args.length) {
                        throw new IllegalArgumentException("missing value for " + args[i]);
                    }
                    opts.put(args[i], args[++i]);
                }
                default -> throw new IllegalArgumentException("unknown option " + args[i]);
            }
        }
        if (!opts.containsKey("--input-dir") || !opts.containsKey("--output-dir")) {
            throw new IllegalArgumentException("--input-dir and --output-dir are required");
        }
        return opts;
    }

    private static <T> List<T> read(Path file, Function<String, T> parser) throws IOException {
        return Files.readAllLines(file, StandardCharsets.US_ASCII).stream()
                .filter(line -> !line.isBlank())
                .map(parser)
                .toList();
    }

    /** Fixed-length records with no delimiters, like a RECFM=F dataset. */
    private static void write(Path file, List<String> records) throws IOException {
        try (OutputStream os = Files.newOutputStream(file)) {
            for (String r : records) {
                os.write(r.getBytes(StandardCharsets.US_ASCII));
            }
        }
    }
}
