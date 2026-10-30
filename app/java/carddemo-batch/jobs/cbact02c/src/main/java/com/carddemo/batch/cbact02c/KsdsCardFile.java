package com.carddemo.batch.cbact02c;

import com.carddemo.batch.common.record.CobolRecord;
import com.carddemo.batch.common.record.FieldSpec;
import com.carddemo.batch.common.record.KeyedFile;
import com.carddemo.batch.common.record.RecordLayout;
import java.io.UncheckedIOException;
import java.nio.charset.CharacterCodingException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;

/**
 * CARDFILE KSDS ({@code AWS.M2.CARDDEMO.CARDDATA.VSAM.KSDS}, {@code KEYS(16 0) RECORDSIZE(150 150)})
 * rebuilt in memory from its ASCII unload. OPEN loads the unload into a {@link KeyedFile} keyed on
 * {@code FD-CARD-NUM}; READ browses it in ascending key order, like a sequential KSDS browse,
 * whatever the order of the unload.
 *
 * <p>OPEN statuses: {@code 35} when the DD is missing or the file does not exist (non-OPTIONAL file
 * not present), {@code 39} when the unload is not a valid image of the 150-byte KSDS (a record longer
 * than 150 bytes, a duplicate key, a non-ASCII byte), {@code 30} for any other I/O error.
 */
final class KsdsCardFile implements CardFile {

    /** FD-CARDFILE-REC: the record area of the file itself, key FD-CARD-NUM. */
    static final RecordLayout FD_CARDFILE_REC = new RecordLayout("FD-CARDFILE-REC", "CBACT02C", List.of(
            FieldSpec.alnum("FD-CARD-NUM", 16),
            FieldSpec.alnum("FD-CARD-DATA", 134)));

    static final String NOT_PRESENT = "35";
    static final String ATTRIBUTE_CONFLICT = "39";
    static final String PERMANENT_ERROR = "30";
    static final String READ_NOT_OPEN = "47";
    static final String CLOSE_NOT_OPEN = "42";

    private final Optional<Path> unload;
    private Iterator<CobolRecord> browse;
    private String openFailure;

    KsdsCardFile(Optional<Path> unload) {
        this.unload = unload;
    }

    @Override
    public String lastError() {
        return openFailure;
    }

    @Override
    public String open() {
        if (unload.isEmpty() || !Files.isRegularFile(unload.get())) {
            openFailure = unload.map(p -> "no such file " + p).orElse("DD CARDFILE not allocated");
            return NOT_PRESENT;
        }
        try {
            browse = KeyedFile.load(unload.get(), FD_CARDFILE_REC, "FD-CARD-NUM").inKeyOrder().iterator();
            return OK;
        } catch (IllegalArgumentException e) {
            openFailure = e.getMessage();
            return ATTRIBUTE_CONFLICT;
        } catch (UncheckedIOException e) {
            openFailure = unload.get().getFileName() + ": " + e.getCause();
            return e.getCause() instanceof CharacterCodingException ? ATTRIBUTE_CONFLICT : PERMANENT_ERROR;
        }
    }

    @Override
    public ReadResult readNext() {
        if (browse == null) {
            return ReadResult.status(READ_NOT_OPEN);
        }
        return browse.hasNext() ? ReadResult.ok(browse.next().toFixedWidth()) : ReadResult.status(END_OF_FILE);
    }

    @Override
    public String close() {
        if (browse == null) {
            return CLOSE_NOT_OPEN;
        }
        browse = null;
        return OK;
    }
}
