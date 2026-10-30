package com.carddemo.batch.cbact03c;

import static com.carddemo.batch.common.record.FieldSpec.alnum;

import com.carddemo.batch.common.record.CobolRecord;
import com.carddemo.batch.common.record.KeyedFile;
import com.carddemo.batch.common.record.RecordLayout;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;

/**
 * The CARDXREF KSDS, built from its ASCII unload with {@link KeyedFile} and browsed in ascending key
 * order. File statuses follow VSAM/GnuCOBOL: 00 ok, 10 end of file, 35 file (or DD) not present,
 * 30 unusable data set (Java-only mapping for an unload that cannot be a KSDS image: duplicate key or a
 * record longer than 50 bytes), 41/42/46/47 for sequence errors that CBACT03C never commits.
 */
final class KsdsXrefFile implements XrefFile {

    /**
     * {@code FD-XREFFILE-REC} as declared in the CBACT03C FD. Unlike {@code Copybooks.CARD_XREF_RECORD} it has
     * no FILLER, so all 50 bytes survive the load and {@code READ ... INTO} / {@code DISPLAY} see them.
     */
    static final RecordLayout FD_XREFFILE_REC = new RecordLayout("FD-XREFFILE-REC", "CBACT03C", List.of(
            alnum("FD-XREF-CARD-NUM", 16),
            alnum("FD-XREF-DATA", 34)));

    static final String OK = "00";
    static final String END_OF_FILE = "10";
    static final String PERMANENT_ERROR = "30";
    static final String NOT_PRESENT = "35";
    static final String ALREADY_OPEN = "41";
    static final String NOT_OPEN = "42";
    static final String NO_NEXT_RECORD = "46";
    static final String NOT_OPEN_INPUT = "47";

    private final Optional<Path> dataset;
    private Iterator<CobolRecord> cursor;
    private boolean atEnd;
    private String record = " ".repeat(FD_XREFFILE_REC.length());

    KsdsXrefFile(Optional<Path> dataset) {
        this.dataset = dataset;
    }

    @Override
    public String open() {
        if (cursor != null) {
            return ALREADY_OPEN;
        }
        if (dataset.isEmpty() || !Files.isRegularFile(dataset.get())) {
            return NOT_PRESENT;
        }
        KeyedFile ksds;
        try {
            ksds = KeyedFile.load(dataset.get(), FD_XREFFILE_REC, "FD-XREF-CARD-NUM");
        } catch (IllegalArgumentException | UncheckedIOException e) {
            return PERMANENT_ERROR;
        }
        cursor = ksds.inKeyOrder().iterator();
        atEnd = false;
        return OK;
    }

    @Override
    public String read() {
        if (cursor == null) {
            return NOT_OPEN_INPUT;
        }
        if (atEnd) {
            return NO_NEXT_RECORD;
        }
        if (!cursor.hasNext()) {
            atEnd = true;
            return END_OF_FILE;
        }
        record = cursor.next().toFixedWidth();
        return OK;
    }

    @Override
    public String record() {
        return record;
    }

    @Override
    public String close() {
        if (cursor == null) {
            return NOT_OPEN;
        }
        cursor = null;
        return OK;
    }
}
