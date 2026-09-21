package com.carddemo.interest.batch;

import com.carddemo.interest.domain.Account;
import com.carddemo.interest.domain.CardXref;
import com.carddemo.interest.domain.DisclosureGroup;
import com.carddemo.interest.repository.InMemoryAccountRepository;
import com.carddemo.interest.repository.InMemoryCardXrefRepository;
import com.carddemo.interest.repository.InMemoryDisclosureGroupRepository;
import com.carddemo.interest.trace.Trace;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Function;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.StepExecutionListener;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;

/**
 * File open/close housekeeping around the chunk step.
 *
 * <p>{@code beforeStep} = 0100-XREFFILE-OPEN / 0200-DISCGRP-OPEN / 0300-ACCTFILE-OPEN: the keyed
 * VSAM files are loaded into the in-memory repositories.
 * {@code afterStep} = the END-OF-FILE branch (line 220, final 1050-UPDATE-ACCOUNT) followed by
 * 9300-ACCTFILE-CLOSE: the rewritten account records are persisted back to the account file in place.
 */
@Trace(program = "CBACT04C", paragraph = "0100/0200/0300-*-OPEN, 9100/9200/9300-*-CLOSE")
public class InterestStepListener implements StepExecutionListener {

    private final Path xrefFile;
    private final Path discgrpFile;
    private final Path acctFile;
    private final InMemoryCardXrefRepository xrefs;
    private final InMemoryDisclosureGroupRepository discgrps;
    private final InMemoryAccountRepository accounts;
    private final InterestProcessor processor;
    private final ItemWriter<InterestItemResult> writer;

    public InterestStepListener(String xrefFile,
                                String discgrpFile,
                                String acctFile,
                                InMemoryCardXrefRepository xrefs,
                                InMemoryDisclosureGroupRepository discgrps,
                                InMemoryAccountRepository accounts,
                                InterestProcessor processor,
                                ItemWriter<InterestItemResult> writer) {
        this.xrefFile = Path.of(xrefFile);
        this.discgrpFile = Path.of(discgrpFile);
        this.acctFile = Path.of(acctFile);
        this.xrefs = xrefs;
        this.discgrps = discgrps;
        this.accounts = accounts;
        this.processor = processor;
        this.writer = writer;
    }

    @Override
    public void beforeStep(StepExecution stepExecution) {
        xrefs.load(readRecords(xrefFile, CardXref::fromRecord));
        discgrps.load(readRecords(discgrpFile, DisclosureGroup::fromRecord));
        accounts.load(readRecords(acctFile, Account::fromRecord));
    }

    @Override
    @Trace(program = "CBACT04C", lines = "219-221")
    public ExitStatus afterStep(StepExecution stepExecution) {
        if (!stepExecution.getStatus().isUnsuccessful()) {
            try {
                writer.write(Chunk.of(processor.flush()));
                writeRecords(acctFile, accounts.findAll(), Account::toRecord);
            } catch (Exception e) {
                throw new IllegalStateException("ERROR RE-WRITING ACCOUNT FILE", e);
            }
        }
        return stepExecution.getExitStatus();
    }

    static <T> List<T> readRecords(Path file, Function<String, T> parser) {
        try (var lines = Files.lines(file, StandardCharsets.UTF_8)) {
            return lines.filter(line -> !line.isEmpty()).map(parser).toList();
        } catch (IOException e) {
            throw new UncheckedIOException("ERROR OPENING " + file, e);
        }
    }

    static <T> void writeRecords(Path file, List<T> records, Function<T, String> formatter) throws IOException {
        Files.write(file, records.stream().map(formatter).toList(), StandardCharsets.UTF_8);
    }
}
